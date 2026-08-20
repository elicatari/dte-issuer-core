package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V1 en Postgres vacío: unique que fallan en rojo, seed Alpha/Beta, rol {@code dte_app}.
 */
@Testcontainers
class FlywayV1Test {

    private static final String OWNER = "dte";
    private static final String OWNER_PASSWORD = "owner-secret";
    private static final String APP = "dte_app";
    private static final String APP_PASSWORD = "app-secret";

    @Container
    static final PostgreSQLContainer postgres = newPostgres();

    @BeforeAll
    static void migrateAsOwner() throws SQLException {
        try (Connection connection = owner(); Statement statement = connection.createStatement()) {
            statement.execute(
                    "create role %s login password '%s' nosuperuser nocreatedb nocreaterole nobypassrls"
                            .formatted(APP, APP_PASSWORD));
            statement.execute("grant connect on database dte_issuer to " + APP);
            statement.execute("grant usage on schema public to " + APP);
        }
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), OWNER, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .placeholders(Map.of("dte_app_role", APP))
                .load()
                .migrate();
    }

    @Test
    void v1CreatesTablesWithTenantIdAndCursorOnTheRangeRow() throws SQLException {
        try (Connection connection = owner();
                ResultSet columns = connection.getMetaData().getColumns(null, "public", null, "tenant_id")) {
            assertThat(columnTables(columns))
                    .containsExactlyInAnyOrder("folio_ranges", "dtes", "idempotency_keys", "outbox");
        }
        try (Connection connection = owner();
                ResultSet nextFolio = connection.getMetaData().getColumns(null, "public", "folio_ranges", "next_folio")) {
            assertThat(nextFolio.next()).isTrue();
        }
        try (Connection connection = app();
                PreparedStatement statement =
                        connection.prepareStatement("select column_name from information_schema.columns "
                                + "where table_name = 'idempotency_keys' order by column_name");
                ResultSet result = statement.executeQuery()) {
            assertThat(columnValues(result, 1)).contains("request_hash", "dte_id", "idempotency_key", "tenant_id");
        }
    }

    @Test
    void seedGivesAlphaAndBetaDistinctNonOverlappingRangesWithCursorAtFrom() throws SQLException {
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "alpha");
            long alphaFrom;
            long alphaTo;
            try (PreparedStatement statement = connection.prepareStatement(
                            "select tenant_id, folio_from, folio_to, next_folio from folio_ranges");
                    ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("tenant_id")).isEqualTo("alpha");
                alphaFrom = result.getLong("folio_from");
                alphaTo = result.getLong("folio_to");
                assertThat(result.getLong("next_folio")).isEqualTo(alphaFrom);
                assertThat(result.next()).isFalse();
            }

            TenantRls.bind(connection, "beta");
            try (PreparedStatement statement = connection.prepareStatement(
                            "select tenant_id, folio_from, folio_to, next_folio from folio_ranges");
                    ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("tenant_id")).isEqualTo("beta");
                long betaFrom = result.getLong("folio_from");
                long betaTo = result.getLong("folio_to");
                assertThat(result.getLong("next_folio")).isEqualTo(betaFrom);
                assertThat(result.next()).isFalse();
                assertThat(alphaTo).isLessThan(betaFrom);
                assertThat(betaTo).isGreaterThanOrEqualTo(betaFrom);
            }
            connection.commit();
        }
    }

    @Test
    void duplicateTenantFolioViolatesUnique() throws SQLException {
        UUID first = insertDte("alpha", 1);
        assertThat(first).isNotNull();
        assertThatThrownBy(() -> insertDte("alpha", 1))
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("23505");
        insertDte("beta", 1);
    }

    @Test
    void duplicateTenantIdempotencyKeyViolatesUnique() throws SQLException {
        insertKey("alpha", "same-key", "hash-a", null);
        assertThatThrownBy(() -> insertKey("alpha", "same-key", "hash-b", null))
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("23505");
        insertKey("beta", "same-key", "hash-a", null);
    }

    @Test
    void appRoleIsNotOwnerSuperuserOrBypassRlsAndCannotDeleteOrDdl() throws SQLException {
        try (Connection connection = owner();
                Statement statement = connection.createStatement();
                ResultSet role = statement.executeQuery(
                        "select rolsuper, rolbypassrls from pg_roles where rolname = '" + APP + "'")) {
            assertThat(role.next()).isTrue();
            assertThat(role.getBoolean("rolsuper")).isFalse();
            assertThat(role.getBoolean("rolbypassrls")).isFalse();
        }
        try (Connection connection = owner();
                Statement statement = connection.createStatement();
                ResultSet owner = statement.executeQuery(
                        "select tableowner from pg_tables where schemaname = 'public' and tablename = 'dtes'")) {
            assertThat(owner.next()).isTrue();
            assertThat(owner.getString("tableowner")).isEqualTo(OWNER);
        }
        UUID id = insertDte("alpha", 42);
        assertThatThrownBy(() -> {
                    try (Connection connection = app();
                            PreparedStatement delete =
                                    connection.prepareStatement("delete from dtes where id = ?")) {
                        delete.setObject(1, id);
                        delete.executeUpdate();
                    }
                })
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("42501");
        assertThatThrownBy(() -> {
                    try (Connection connection = app(); Statement statement = connection.createStatement()) {
                        statement.execute("drop table dtes");
                    }
                })
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("42501");
    }

    @Test
    void appRoleCanUpdateFolioCursorAndLinkIdempotencyKeyToDte() throws SQLException {
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "alpha");
            try (PreparedStatement update = connection.prepareStatement(
                    "update folio_ranges set next_folio = next_folio + 1 where tenant_id = 'alpha'")) {
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
        }
        UUID dteId = insertDte("alpha", 7);
        insertKey("alpha", "issued-key", "a".repeat(64), dteId);
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "alpha");
            try (PreparedStatement statement = connection.prepareStatement(
                            "select dte_id from idempotency_keys where tenant_id = 'alpha' and idempotency_key = 'issued-key'");
                    ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject("dte_id", UUID.class)).isEqualTo(dteId);
            }
            connection.commit();
        }
    }

    @Test
    void v3OutboxHasTenantIdAndAppCanInsertUpdateButNotDelete() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "alpha");
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into outbox (id, tenant_id, event_name, event_version, payload, occurred_at) "
                            + "values (?, 'alpha', 'DteIssued', '1', '{}', now())")) {
                insert.setObject(1, id);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement update =
                    connection.prepareStatement("update outbox set published_at = now() where id = ?")) {
                update.setObject(1, id);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
        }
        assertThatThrownBy(() -> {
                    try (Connection connection = app();
                            PreparedStatement delete =
                                    connection.prepareStatement("delete from outbox where id = ?")) {
                        delete.setObject(1, id);
                        delete.executeUpdate();
                    }
                })
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("42501");
    }

    private static UUID insertDte(String tenantId, long folio) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into dtes (id, tenant_id, folio, rut, document_type, neto, iva, total, status, issued_at) "
                            + "values (?, ?, ?, '12345678-5', 'BOLETA_39', 1000, 190, 1190, 'ISSUED', now())")) {
                statement.setObject(1, id);
                statement.setString(2, tenantId);
                statement.setLong(3, folio);
                statement.executeUpdate();
            }
            connection.commit();
        }
        return id;
    }

    private static void insertKey(String tenantId, String key, String hash, UUID dteId) throws SQLException {
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into idempotency_keys (tenant_id, idempotency_key, request_hash, dte_id) "
                            + "values (?, ?, ?, ?)")) {
                statement.setString(1, tenantId);
                statement.setString(2, key);
                statement.setString(3, hash);
                statement.setObject(4, dteId);
                statement.executeUpdate();
            }
            connection.commit();
        }
    }

    private static PostgreSQLContainer newPostgres() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17");
        container.withDatabaseName("dte_issuer");
        container.withUsername(OWNER);
        container.withPassword(OWNER_PASSWORD);
        return container;
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), APP, APP_PASSWORD);
    }

    private static List<String> columnTables(ResultSet columns) throws SQLException {
        List<String> tables = new ArrayList<>();
        while (columns.next()) {
            tables.add(columns.getString("TABLE_NAME"));
        }
        return tables;
    }

    private static List<String> columnValues(ResultSet result, int index) throws SQLException {
        List<String> values = new ArrayList<>();
        while (result.next()) {
            values.add(result.getString(index));
        }
        return values;
    }
}
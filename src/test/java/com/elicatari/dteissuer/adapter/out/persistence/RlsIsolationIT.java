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

/**
 * SQL nativo como {@code dte_app}, sin Hibernate ni repositorios. El
 * {@code @Filter} no puede aprobar este test.
 */
class RlsIsolationIT extends AbstractJpaPostgresTest {

    @BeforeAll
    static void migrateAsOwner() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), OWNER, OWNER_PASSWORD)
                .locations("classpath:db/migration")
                .placeholders(Map.of("dte_app_role", APP))
                .load()
                .migrate();
    }

    @Test
    void nativeSelectAsBetaDoesNotReturnAlphaRows() throws SQLException {
        UUID alphaId = insertDte("alpha", 9001);
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "beta");
            try (PreparedStatement statement = connection.prepareStatement("select id from dtes");
                    ResultSet result = statement.executeQuery()) {
                assertThat(ids(result)).doesNotContain(alphaId);
            }
            connection.commit();
        }
    }

    @Test
    void insertWithForeignTenantIdFailsWithCheck() {
        assertThatThrownBy(() -> {
                    try (Connection connection = app()) {
                        connection.setAutoCommit(false);
                        TenantRls.bind(connection, "beta");
                        try (PreparedStatement statement = connection.prepareStatement(
                                "insert into dtes (id, tenant_id, folio, rut, document_type, neto, iva, total, status, issued_at) "
                                        + "values (?, 'alpha', 9002, '12345678-5', 'BOLETA_39', 1000, 190, 1190, 'ISSUED', now())")) {
                            statement.setObject(1, UUID.randomUUID());
                            statement.executeUpdate();
                        }
                        connection.commit();
                    }
                })
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("42501");
    }

    @Test
    void updateToForeignTenantIdFailsWithCheck() throws SQLException {
        UUID id = insertDte("alpha", 9003);
        assertThatThrownBy(() -> {
                    try (Connection connection = app()) {
                        connection.setAutoCommit(false);
                        TenantRls.bind(connection, "alpha");
                        try (PreparedStatement statement =
                                connection.prepareStatement("update dtes set tenant_id = 'beta' where id = ?")) {
                            statement.setObject(1, id);
                            statement.executeUpdate();
                        }
                        connection.commit();
                    }
                })
                .isInstanceOf(SQLException.class)
                .extracting(ex -> ((SQLException) ex).getSQLState())
                .isEqualTo("42501");
    }

    @Test
    void withoutSessionVariableZeroRowsNeverAll() throws SQLException {
        UUID alphaDte = insertDte("alpha", 9004);
        UUID alphaEvent = insertOutbox("alpha");
        try (Connection connection = app();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select count(*) from dtes")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
        try (Connection connection = app();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select count(*) from folio_ranges")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
        try (Connection connection = app();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select count(*) from outbox")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "alpha");
            try (PreparedStatement dtes = connection.prepareStatement("select id from dtes");
                    ResultSet dteRows = dtes.executeQuery();
                    PreparedStatement events = connection.prepareStatement("select id from outbox");
                    ResultSet eventRows = events.executeQuery()) {
                assertThat(ids(dteRows)).contains(alphaDte);
                assertThat(ids(eventRows)).contains(alphaEvent);
            }
            connection.commit();
        }
    }

    @Test
    void sameConnectionConsecutiveTenantsDoNotSeeEachOther() throws SQLException {
        UUID alphaId = insertDte("alpha", 9005);
        UUID betaId = insertDte("beta", 8001);
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "alpha");
            try (PreparedStatement statement = connection.prepareStatement("select id from dtes");
                    ResultSet result = statement.executeQuery()) {
                assertThat(ids(result)).contains(alphaId).doesNotContain(betaId);
            }
            connection.commit();

            connection.setAutoCommit(false);
            TenantRls.bind(connection, "beta");
            try (PreparedStatement statement = connection.prepareStatement("select id from dtes");
                    ResultSet result = statement.executeQuery()) {
                assertThat(ids(result)).contains(betaId).doesNotContain(alphaId);
            }
            connection.commit();
        }
    }

    @Test
    void forceRlsAndPoliciesAreOnAllTenantTables() throws SQLException {
        try (Connection connection = owner();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select c.relname, c.relrowsecurity, c.relforcerowsecurity "
                                + "from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                                + "where n.nspname = 'public' and c.relname in ('dtes', 'folio_ranges', 'idempotency_keys', 'outbox') "
                                + "order by c.relname")) {
            int tables = 0;
            while (result.next()) {
                assertThat(result.getBoolean("relrowsecurity")).isTrue();
                assertThat(result.getBoolean("relforcerowsecurity")).isTrue();
                tables++;
            }
            assertThat(tables).isEqualTo(4);
        }
        try (Connection connection = owner();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select tablename from pg_policies where schemaname = 'public' and policyname = 'tenant_isolation'")) {
            assertThat(names(result)).containsExactlyInAnyOrder("dtes", "folio_ranges", "idempotency_keys", "outbox");
        }
    }

    @Test
    void nativeSelectAsBetaDoesNotReturnAlphaOutboxAndRelaySeesAll() throws SQLException {
        UUID alphaEvent = insertOutbox("alpha");
        UUID betaEvent = insertOutbox("beta");
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, "beta");
            try (PreparedStatement statement = connection.prepareStatement("select id from outbox");
                    ResultSet result = statement.executeQuery()) {
                assertThat(ids(result)).contains(betaEvent).doesNotContain(alphaEvent);
            }
            connection.commit();

            connection.setAutoCommit(false);
            TenantRls.bindRelay(connection);
            try (PreparedStatement statement = connection.prepareStatement("select id from outbox");
                    ResultSet result = statement.executeQuery()) {
                assertThat(ids(result)).contains(alphaEvent, betaEvent);
            }
            connection.commit();
        }
    }

    @Test
    void insertOutboxWithForeignTenantIdFailsWithCheck() {
        assertThatThrownBy(() -> {
                    try (Connection connection = app()) {
                        connection.setAutoCommit(false);
                        TenantRls.bind(connection, "beta");
                        try (PreparedStatement statement = connection.prepareStatement(
                                "insert into outbox (id, tenant_id, event_name, event_version, payload, occurred_at) "
                                        + "values (?, 'alpha', 'DteIssued', '1', '{}', now())")) {
                            statement.setObject(1, UUID.randomUUID());
                            statement.executeUpdate();
                        }
                        connection.commit();
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

    private static UUID insertOutbox(String tenantId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = app()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into outbox (id, tenant_id, event_name, event_version, payload, occurred_at) "
                            + "values (?, ?, 'DteIssued', '1', '{}', now())")) {
                statement.setObject(1, id);
                statement.setString(2, tenantId);
                statement.executeUpdate();
            }
            connection.commit();
        }
        return id;
    }

    private static List<UUID> ids(ResultSet result) throws SQLException {
        List<UUID> values = new ArrayList<>();
        while (result.next()) {
            values.add(result.getObject(1, UUID.class));
        }
        return values;
    }

    private static List<String> names(ResultSet result) throws SQLException {
        List<String> values = new ArrayList<>();
        while (result.next()) {
            values.add(result.getString(1));
        }
        return values;
    }

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), OWNER, OWNER_PASSWORD);
    }

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), APP, APP_PASSWORD);
    }
}
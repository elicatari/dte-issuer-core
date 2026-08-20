package com.elicatari.dteissuer.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Un Postgres por JVM: {@code @Container} lo apaga al cerrar la primera clase y el
 * contexto Spring cacheado queda apuntando a un puerto muerto.
 */
public abstract class AbstractJpaPostgresTest {

    static final String OWNER = "dte";
    static final String OWNER_PASSWORD = "owner-secret";
    static final String APP = "dte_app";
    static final String APP_PASSWORD = "app-secret";

    static final PostgreSQLContainer postgres;

    static {
        postgres = newPostgres();
        postgres.start();
    }

    private static PostgreSQLContainer newPostgres() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17");
        container.withDatabaseName("dte_issuer");
        container.withUsername(OWNER);
        container.withPassword(OWNER_PASSWORD);
        container.withInitScript("db/test/create-app-role.sql");
        return container;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", () -> OWNER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("spring.flyway.placeholders.[dte_app_role]", () -> APP);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "10000");
    }

    /**
     * CAF de un tenant de prueba. RLS exige {@code app.tenant_id} alineado con la fila.
     */
    static void insertFolioRange(DataSource dataSource, String tenantId, long from, long to)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            TenantRls.bind(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into folio_ranges (id, tenant_id, folio_from, folio_to, next_folio) "
                            + "values (?, ?, ?, ?, ?)")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, tenantId);
                statement.setLong(3, from);
                statement.setLong(4, to);
                statement.setLong(5, from);
                statement.executeUpdate();
            }
            connection.commit();
        }
    }
}
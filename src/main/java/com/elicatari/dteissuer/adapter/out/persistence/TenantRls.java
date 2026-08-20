package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.domain.TenantId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Fija {@code app.tenant_id} con {@code set_config(..., true)}: local a la
 * transacción. {@code false} dejaría el tenant pegado a la conexión del pool.
 */
final class TenantRls {

    static final String SETTING = "app.tenant_id";
    static final String RELAY_SETTING = "app.outbox_relay";

    private TenantRls() {}

    static void bind(Connection connection, TenantId tenantId) throws SQLException {
        bind(connection, tenantId.value());
    }

    static void bind(Connection connection, String tenantId) throws SQLException {
        setConfig(connection, SETTING, tenantId);
    }

    /**
     * El poller in-process ve filas de todos los tenants. Local a la transacción:
     * no se arrastra en el pool. No es {@code BYPASSRLS} ni un segundo proceso.
     */
    static void bindRelay(Connection connection) throws SQLException {
        setConfig(connection, RELAY_SETTING, "true");
    }

    private static void setConfig(Connection connection, String name, String value) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("select set_config(?, ?, true)")) {
            statement.setString(1, name);
            statement.setString(2, value);
            statement.execute();
        }
    }
}
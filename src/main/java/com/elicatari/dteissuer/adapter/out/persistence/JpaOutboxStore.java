package com.elicatari.dteissuer.adapter.out.persistence;

import com.elicatari.dteissuer.domain.DteIssued;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

/**
 * Outbox en la misma persistencia que el DTE. El use case no conoce esta tabla.
 */
@Repository
public class JpaOutboxStore {

    private final EntityManager entityManager;
    private final DataSource dataSource;

    JpaOutboxStore(EntityManager entityManager, DataSource dataSource) {
        this.entityManager = entityManager;
        this.dataSource = dataSource;
    }

    public void append(DteIssued event, String eventName, String eventVersion, String payload) {
        TenantSession.bind(entityManager, event.tenantId());
        entityManager.persist(new OutboxEntity(
                event.eventId(),
                event.tenantId().value(),
                eventName,
                eventVersion,
                payload,
                event.occurredAt()));
        entityManager.flush();
    }

    /**
     * Mismas filas que reclama el poller. JDBC propio y {@code app.outbox_relay}
     * local a esta TX: no hereda ni deja {@code app.tenant_id} en el pool.
     */
    public OutboxPendingStats unpublishedStats(Instant cutoff) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TenantRls.bindRelay(connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        select count(*) as pending_count, min(occurred_at) as oldest
                        from outbox
                        where published_at is null
                          and occurred_at < ?
                        """)) {
                    statement.setTimestamp(1, Timestamp.from(cutoff));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        long count = result.getLong("pending_count");
                        Timestamp oldest = result.getTimestamp("oldest");
                        connection.commit();
                        return new OutboxPendingStats(
                                count, oldest == null ? null : oldest.toInstant());
                    }
                }
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("no se pudo leer outbox pendiente", ex);
        }
    }

    public List<OutboxRecord> claimUnpublishedBatch(Instant cutoff, int batchSize) {
        Session session = entityManager.unwrap(Session.class);
        session.doWork(TenantRls::bindRelay);
        int limit = Math.max(1, batchSize);
        String sql =
                """
                select id, tenant_id, event_name, event_version, payload, occurred_at, published_at
                from outbox
                where published_at is null
                  and occurred_at < :cutoff
                order by occurred_at
                for update skip locked
                limit %d
                """
                        .formatted(limit);
        return session
                .createNativeQuery(sql, OutboxEntity.class)
                .setParameter("cutoff", cutoff)
                .getResultList()
                .stream()
                .map(JpaOutboxStore::toRecord)
                .toList();
    }

    public void markPublished(UUID eventId, Instant publishedAt) {
        OutboxEntity entity = entityManager.find(OutboxEntity.class, eventId);
        if (entity != null && entity.publishedAt() == null) {
            entity.markPublished(publishedAt);
        }
    }

    /**
     * Marca publicado sin JPA. Para el listener {@code AFTER_COMMIT}: en
     * {@code afterCompletion} un EntityManager nuevo no enlaza bien.
     */
    public void markPublishedDirect(UUID eventId, Instant publishedAt) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                TenantRls.bindRelay(connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        "update outbox set published_at = ? where id = ? and published_at is null")) {
                    statement.setTimestamp(1, Timestamp.from(publishedAt));
                    statement.setObject(2, eventId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("no se pudo marcar outbox publicado " + eventId, ex);
        }
    }

    private static OutboxRecord toRecord(OutboxEntity entity) {
        return new OutboxRecord(
                entity.id(),
                entity.tenantId(),
                entity.eventName(),
                entity.eventVersion(),
                entity.payload(),
                entity.occurredAt());
    }
}
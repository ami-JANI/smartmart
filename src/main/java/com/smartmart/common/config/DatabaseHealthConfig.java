package com.smartmart.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * One health check per database server (SCRUM-59), shown in
 * /actuator/health as primaryDatabase and replicaDatabase. Each asks its own
 * server directly, not through the routing data source.
 */
@Configuration
class DatabaseHealthConfig {

    @Bean
    HealthIndicator primaryDatabase(@Qualifier("primaryDataSource") DataSource primary) {
        return () -> check(primary, "primary");
    }

    @Bean
    HealthIndicator replicaDatabase(@Qualifier("replicaDataSource") DataSource replica) {
        return () -> check(replica, "replica");
    }

    // inRecovery is true on a streaming replica. lagSeconds is how old the last replayed change is,
    // or empty on the primary and on a replica that has replayed nothing yet.
    private static Health check(DataSource dataSource, String server) {
        try {
            return new JdbcTemplate(dataSource).queryForObject("""
                            select pg_is_in_recovery() as in_recovery,
                                   extract(epoch from now() - pg_last_xact_replay_timestamp()) as lag""",
                    (row, n) -> {
                        Health.Builder health = Health.up().withDetail("server", server)
                                .withDetail("inRecovery", row.getBoolean("in_recovery"));
                        double lag = row.getDouble("lag");
                        if (!row.wasNull()) {
                            health.withDetail("lagSeconds", Math.round(lag * 10) / 10.0);
                        }
                        return health.build();
                    });
        } catch (RuntimeException failure) {
            return Health.down().withDetail("server", server).withDetail("error", failure.getClass().getSimpleName()).build();
        }
    }
}

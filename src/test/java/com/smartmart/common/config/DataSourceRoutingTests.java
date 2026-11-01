package com.smartmart.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import liquibase.integration.spring.SpringLiquibase;

/**
 * Runs against the two databases from compose.yaml. {@code pg_is_in_recovery()}
 * is true only on the replica, so it shows which server answered.
 */
@SpringBootTest
class DataSourceRoutingTests {

    private static final String IS_REPLICA = "select pg_is_in_recovery()";

    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbc;
    private final SpringLiquibase liquibase;
    private final DataSource primaryDataSource;

    @Autowired
    DataSourceRoutingTests(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                           SpringLiquibase liquibase, @Qualifier("primaryDataSource") DataSource primaryDataSource) {
        this.transactionManager = transactionManager;
        this.jdbc = jdbc;
        this.liquibase = liquibase;
        this.primaryDataSource = primaryDataSource;
    }

    @Test
    void readOnlyTransactionRunsOnReplica() {
        assertThat(answeredByReplica(true)).isTrue();
    }

    @Test
    void readWriteTransactionRunsOnPrimary() {
        assertThat(answeredByReplica(false)).isFalse();
    }

    @Test
    void workOutsideTransactionRunsOnPrimary() {
        assertThat(jdbc.queryForObject(IS_REPLICA, Boolean.class)).isFalse();
    }

    @Test
    void liquibaseRunsOnlyOnPrimary() {
        assertThat(liquibase.getDataSource()).isSameAs(primaryDataSource);
    }

    private Boolean answeredByReplica(boolean readOnly) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(readOnly);
        return transaction.execute(status -> jdbc.queryForObject(IS_REPLICA, Boolean.class));
    }
}

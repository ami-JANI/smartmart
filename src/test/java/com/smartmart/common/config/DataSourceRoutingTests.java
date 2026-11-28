package com.smartmart.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;
    private final SpringLiquibase liquibase;
    private final DataSource primaryDataSource;
    private final ApplicationContext context;

    @Autowired
    DataSourceRoutingTests(PlatformTransactionManager transactionManager, JdbcTemplate jdbc,
                           EntityManager entityManager, SpringLiquibase liquibase,
                           @Qualifier("primaryDataSource") DataSource primaryDataSource,
                           ApplicationContext context) {
        this.transactionManager = transactionManager;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.liquibase = liquibase;
        this.primaryDataSource = primaryDataSource;
        this.context = context;
    }

    @Test
    void readOnlyTransactionRunsOnReplica() {
        assertThat(inTransaction(true, () -> jdbc.queryForObject(IS_REPLICA, Boolean.class))).isTrue();
    }

    @Test
    void readWriteTransactionRunsOnPrimary() {
        assertThat(inTransaction(false, () -> jdbc.queryForObject(IS_REPLICA, Boolean.class))).isFalse();
    }

    @Test
    void readWriteJpaTransactionAfterReadOnlyStillRunsOnPrimary() {
        assertThat(inTransaction(true, () -> (Boolean) entityManager.createNativeQuery(IS_REPLICA).getSingleResult())).isTrue();
        assertThat(inTransaction(false, () -> (Boolean) entityManager.createNativeQuery(IS_REPLICA).getSingleResult())).isFalse();
    }

    @Test
    void openInViewIsDisabledSoConnectionsAreNotPinnedAcrossTransactions() {
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    @Test
    void workOutsideTransactionRunsOnPrimary() {
        assertThat(jdbc.queryForObject(IS_REPLICA, Boolean.class)).isFalse();
    }

    @Test
    void liquibaseRunsOnlyOnPrimary() {
        assertThat(liquibase.getDataSource()).isSameAs(primaryDataSource);
    }

    private Boolean inTransaction(boolean readOnly, Supplier<Boolean> query) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(readOnly);
        return transaction.execute(status -> query.get());
    }
}

package com.smartmart.common.config;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes each connection to the replica when the current transaction is
 * read-only, and to the primary otherwise (GUIDELINES.md section 8.2).
 * Work outside a transaction goes to the primary.
 * <p>
 * Must be wrapped in a {@link org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy},
 * so the connection is taken only after Spring has marked the transaction read-only.
 */
class ReadOnlyRoutingDataSource extends AbstractRoutingDataSource {

    enum Target { PRIMARY, REPLICA }

    ReadOnlyRoutingDataSource(DataSource primary, DataSource replica) {
        setTargetDataSources(Map.<Object, Object>of(Target.PRIMARY, primary, Target.REPLICA, replica));
        setDefaultTargetDataSource(primary);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? Target.REPLICA : Target.PRIMARY;
    }
}

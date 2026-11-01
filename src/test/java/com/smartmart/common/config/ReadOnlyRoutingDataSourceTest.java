package com.smartmart.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.smartmart.common.config.ReadOnlyRoutingDataSource.Target;

class ReadOnlyRoutingDataSourceTest {

    private final ReadOnlyRoutingDataSource routing =
            new ReadOnlyRoutingDataSource(mock(DataSource.class), mock(DataSource.class));

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void outsideTransactionUsesPrimary() {
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(Target.PRIMARY);
    }

    @Test
    void readOnlyTransactionUsesReplica() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(routing.determineCurrentLookupKey()).isEqualTo(Target.REPLICA);
    }

    @Test
    void readWriteTransactionUsesPrimary() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertThat(routing.determineCurrentLookupKey()).isEqualTo(Target.PRIMARY);
    }
}

package com.smartmart.common.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Connection pools for the primary and the replica, and the routing data
 * source the rest of the application uses (GUIDELINES.md section 8.2).
 * Services never pick a database themselves: they mark read-only work with
 * {@code @Transactional(readOnly = true)}.
 */
@Configuration
class DataSourceConfig {

    /** Takes every write. Liquibase runs only here; the replica gets schema changes through replication. */
    @Bean
    @LiquibaseDataSource
    @ConfigurationProperties("smartmart.datasource.primary")
    HikariDataSource primaryDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /** Read-only hot standby, kept up to date by streaming replication. */
    @Bean
    @ConfigurationProperties("smartmart.datasource.replica")
    HikariDataSource replicaDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    DataSource dataSource(@Qualifier("primaryDataSource") DataSource primaryDataSource,
                          @Qualifier("replicaDataSource") DataSource replicaDataSource) {
        return new LazyConnectionDataSourceProxy(new ReadOnlyRoutingDataSource(primaryDataSource, replicaDataSource));
    }
}

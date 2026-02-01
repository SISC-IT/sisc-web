package org.sejongisc.backend.common.config.db;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@Profile("dev")
@EnableJpaRepositories(
        basePackages = "org.sejongisc.backend.stock.repository",
        entityManagerFactoryRef = "stockEntityManagerFactory",
        transactionManagerRef = "stockTransactionManager"
)
public class StockDataSourceConfig {

    @Bean(name = "stockDataSourceProperties")
    @ConfigurationProperties("spring.stock.datasource")
    public DataSourceProperties stockDataSourceProperties() {
        return new DataSourceProperties();
    }

    // ✅ HikariConfig에 먼저 바인딩
    @Bean(name = "stockHikariConfig")
    @ConfigurationProperties("spring.stock.datasource.hikari")
    public com.zaxxer.hikari.HikariConfig stockHikariConfig() {
        return new com.zaxxer.hikari.HikariConfig();
    }

    @Bean(name = "stockDataSource")
    public DataSource stockDataSource(
            @Qualifier("stockDataSourceProperties") DataSourceProperties props,
            @Qualifier("stockHikariConfig") com.zaxxer.hikari.HikariConfig hkCfg) {

        hkCfg.setJdbcUrl(props.getUrl());
        hkCfg.setUsername(props.getUsername());
        hkCfg.setPassword(props.getPassword());
        if (props.getDriverClassName() != null) {
            hkCfg.setDriverClassName(props.getDriverClassName());
        }
        return new HikariDataSource(hkCfg);
    }

    @Bean(name = "stockEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean stockEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("stockDataSource") DataSource dataSource) {

        Map<String, Object> jpaProps = new HashMap<>();
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProps.put("hibernate.default_schema", "public");
        // ddl-auto는 yml로 관리 권장
        jpaProps.put("hibernate.hbm2ddl.auto", "none");

        return builder
                .dataSource(dataSource)
                .packages("org.sejongisc.backend.stock.entity")
                .persistenceUnit("stock")
                .properties(jpaProps)
                .build();
    }

    @Bean(name = "stockTransactionManager")
    public PlatformTransactionManager stockTransactionManager(
            @Qualifier("stockEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}

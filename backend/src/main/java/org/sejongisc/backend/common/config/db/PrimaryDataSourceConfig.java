package org.sejongisc.backend.common.config.db;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.*;

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
@Profile("prod")
@EnableJpaRepositories(
        basePackages = "org.sejongisc.backend",
        entityManagerFactoryRef = "primaryEntityManagerFactory",
        transactionManagerRef = "primaryTransactionManager",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org\\.sejongisc\\.backend\\.stock\\.repository\\..*"
        )
)
public class PrimaryDataSourceConfig {

    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    // ✅ HikariConfig에 먼저 바인딩 (풀 아직 시작 안 됨)
    @Primary
    @Bean(name = "primaryHikariConfig")
    @ConfigurationProperties("spring.datasource.hikari")
    public com.zaxxer.hikari.HikariConfig primaryHikariConfig() {
        return new com.zaxxer.hikari.HikariConfig();
    }

    // ✅ HikariConfig로 HikariDataSource "생성 시" 설정을 반영
    @Primary
    @Bean(name = "primaryDataSource")
    public DataSource primaryDataSource(
            @Qualifier("primaryDataSourceProperties") DataSourceProperties props,
            @Qualifier("primaryHikariConfig") com.zaxxer.hikari.HikariConfig hkCfg) {

        // URL/계정 정보는 props에서
        hkCfg.setJdbcUrl(props.getUrl());
        hkCfg.setUsername(props.getUsername());
        hkCfg.setPassword(props.getPassword());
        if (props.getDriverClassName() != null) {
            hkCfg.setDriverClassName(props.getDriverClassName());
        }
        return new HikariDataSource(hkCfg); // ← 생성 시점에 봉인되며 이후 변경 시도 없음
    }

    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("primaryDataSource") DataSource dataSource) {

        Map<String, Object> jpaProps = new HashMap<>();
        jpaProps.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        jpaProps.put("hibernate.hbm2ddl.auto", "update");

        return builder
                .dataSource(dataSource)
                .packages(
                        "org.sejongisc.backend.attendance.entity",
                        "org.sejongisc.backend.common.auth.entity",
                        "org.sejongisc.backend.backtest.entity",
                        "org.sejongisc.backend.betting.entity",
                        "org.sejongisc.backend.board.entity",
                        "org.sejongisc.backend.common.entity.postgres",
                        "org.sejongisc.backend.point.entity",
                        "org.sejongisc.backend.user.entity"
                )
                .persistenceUnit("primary")
                .properties(jpaProps)
                .build();
    }

    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}

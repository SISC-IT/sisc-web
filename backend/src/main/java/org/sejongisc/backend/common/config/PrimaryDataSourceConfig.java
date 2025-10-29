package org.sejongisc.backend.common.config;

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

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "org.sejongisc.backend",         // 전체 패키지 스캔
    entityManagerFactoryRef = "primaryEntityManagerFactory",    // 아래 Bean 이름과 일치
    transactionManagerRef = "primaryTransactionManager",        // 아래 Bean 이름과 일치
    excludeFilters = @ComponentScan.Filter(         // 특정 패키지 제외 (Stock 관련)
        type = FilterType.REGEX,
        pattern = "org\\.sejongisc\\.backend\\.stock\\.repository\\..*"
    )
)
public class PrimaryDataSourceConfig {

    @Primary
    @Bean(name = "primaryDataSourceProperties")
    @ConfigurationProperties("spring.datasource") // 표준 경로 사용
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "primaryDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource primaryDataSource(@Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "primaryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("primaryDataSource") DataSource dataSource) {
        return builder
            .dataSource(dataSource)
            .packages(
                "org.sejongisc.backend.attendance.entity",
                "org.sejongisc.backend.auth.entity",
                "org.sejongisc.backend.backtest.entity",
                "org.sejongisc.backend.betting.entity",
                "org.sejongisc.backend.common.entity.postgres",
                "org.sejongisc.backend.point.entity",
                "org.sejongisc.backend.stock.entity",
                "org.sejongisc.backend.template.entity",
                "org.sejongisc.backend.user.entity"
            )
            .persistenceUnit("primary")
            .build();
    }

    @Primary
    @Bean(name = "primaryTransactionManager")
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}


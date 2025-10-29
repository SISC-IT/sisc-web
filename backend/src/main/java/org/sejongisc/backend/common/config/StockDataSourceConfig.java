package org.sejongisc.backend.common.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@EnableJpaRepositories(
    basePackages = "org.sejongisc.backend.stock.repository",    // PriceDataRepository 가 있는 패키지만 스캔하도록 지정
    entityManagerFactoryRef = "stockEntityManagerFactory",      // 아래 Bean 이름과 일치
    transactionManagerRef = "stockTransactionManager"           // 아래 Bean 이름과 일치
)
public class StockDataSourceConfig {

    @Bean(name = "stockDataSourceProperties")
    @ConfigurationProperties("spring.stock.datasource")         // yml의 'stock.datasource' 참조
    public DataSourceProperties stockDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "stockDataSource")
    @ConfigurationProperties("spring.stock.datasource.hikari")
    public DataSource stockDataSource(@Qualifier("stockDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "stockEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean stockEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("stockDataSource") DataSource dataSource) {

        return builder
            .dataSource(dataSource)
            .packages("org.sejongisc.backend.stock.entity")     // PriceData 엔티티가 있는 패키지 지정
            .persistenceUnit("stock")           // Persistence Unit 이름
            .build();
    }

    @Bean(name = "stockTransactionManager")
    public PlatformTransactionManager stockTransactionManager(
        @Qualifier("stockEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

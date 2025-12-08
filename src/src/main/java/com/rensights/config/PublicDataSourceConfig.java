package com.rensights.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.rensights.model.Deal;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.rensights.repository",
    includeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {com.rensights.repository.DealRepository.class})
    },
    entityManagerFactoryRef = "publicEntityManagerFactory",
    transactionManagerRef = "publicTransactionManager"
)
public class PublicDataSourceConfig {

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Bean(name = "publicDataSourceProperties")
    @ConfigurationProperties("spring.public-datasource")
    public DataSourceProperties publicDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "publicDataSource")
    public DataSource publicDataSource(@Qualifier("publicDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    @Bean(name = "publicEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean publicEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("publicDataSource") DataSource dataSource) {
        Map<String, String> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        
        // SECURITY FIX: Only enable SQL logging in dev profile to prevent sensitive data exposure in production
        boolean isDev = activeProfile != null && activeProfile.contains("dev");
        properties.put("hibernate.format_sql", isDev ? "true" : "false");
        properties.put("hibernate.show_sql", isDev ? "true" : "false");
        properties.put("hibernate.archive.autodetection", "none");
        
        // Use builder with packages() but set ddl-auto to none to prevent validation
        // The repository filter already ensures only DealRepository is used
        return builder
            .dataSource(dataSource)
            .packages(Deal.class)
            .persistenceUnit("public")
            .properties(properties)
            .build();
    }

    @Bean(name = "publicTransactionManager")
    public PlatformTransactionManager publicTransactionManager(
            @Qualifier("publicEntityManagerFactory") LocalContainerEntityManagerFactoryBean publicEntityManagerFactory) {
        return new JpaTransactionManager(publicEntityManagerFactory.getObject());
    }
}


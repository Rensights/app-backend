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
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.beans.factory.annotation.Value;

import com.rensights.model.AnalysisRequest;
import com.rensights.model.Device;
import com.rensights.model.Invoice;
import com.rensights.model.Subscription;
import com.rensights.model.User;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.rensights.repository",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {com.rensights.repository.DealRepository.class})
    },
    entityManagerFactoryRef = "adminEntityManagerFactory",
    transactionManagerRef = "adminTransactionManager"
)
public class AdminDataSourceConfig {

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Primary
    @Bean(name = "adminDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties adminDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "adminDataSource")
    public DataSource adminDataSource(@Qualifier("adminDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
    }

    @Primary
    @Bean(name = "adminEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean adminEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("adminDataSource") DataSource dataSource) {
        Map<String, String> properties = new HashMap<>();
        // Read ddl-auto from application config, default to 'update' for now
        // TODO: Change back to 'validate' after invoices table is created
        properties.put("hibernate.hbm2ddl.auto", "update");
        
        // SECURITY FIX: Only enable SQL logging in dev profile to prevent sensitive data exposure in production
        boolean isDev = activeProfile != null && activeProfile.contains("dev");
        properties.put("hibernate.format_sql", isDev ? "true" : "false");
        properties.put("hibernate.show_sql", isDev ? "true" : "false");
        
        return builder
            .dataSource(dataSource)
            .packages(User.class, Device.class, Subscription.class, AnalysisRequest.class, Invoice.class)
            .persistenceUnit("admin")
            .properties(properties)
            .build();
    }

    @Primary
    @Bean(name = "adminTransactionManager")
    public PlatformTransactionManager adminTransactionManager(
            @Qualifier("adminEntityManagerFactory") LocalContainerEntityManagerFactoryBean adminEntityManagerFactory) {
        return new JpaTransactionManager(adminEntityManagerFactory.getObject());
    }
}





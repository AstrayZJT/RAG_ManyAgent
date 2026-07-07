package com.astray.insightflow.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    private static final String DEFAULT_H2_URL =
            "jdbc:h2:file:./build/insightflow-db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

    @Bean
    public DataSource dataSource(Environment environment) {
        String explicitUrl = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("DB_URL")
        );
        if (StringUtils.hasText(explicitUrl)) {
            return createDataSource(
                    explicitUrl,
                    firstNonBlank(environment.getProperty("SPRING_DATASOURCE_USERNAME"), environment.getProperty("DB_USERNAME"), "sa"),
                    firstNonBlank(environment.getProperty("SPRING_DATASOURCE_PASSWORD"), environment.getProperty("DB_PASSWORD"), ""),
                    firstNonBlank(environment.getProperty("SPRING_DATASOURCE_DRIVER_CLASS_NAME"), inferDriver(explicitUrl))
            );
        }

        String dbHost = environment.getProperty("DB_HOST");
        String dbName = environment.getProperty("DB_NAME");
        if (StringUtils.hasText(dbHost) || StringUtils.hasText(dbName)) {
            String host = firstNonBlank(dbHost, "localhost");
            String port = firstNonBlank(environment.getProperty("DB_PORT"), "5432");
            String database = firstNonBlank(dbName, "agentdemo");
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            return createDataSource(
                    jdbcUrl,
                    firstNonBlank(environment.getProperty("DB_USERNAME"), "postgres"),
                    firstNonBlank(environment.getProperty("DB_PASSWORD"), ""),
                    "org.postgresql.Driver"
            );
        }

        return createDataSource(
                DEFAULT_H2_URL,
                firstNonBlank(environment.getProperty("SPRING_DATASOURCE_USERNAME"), "sa"),
                firstNonBlank(environment.getProperty("SPRING_DATASOURCE_PASSWORD"), ""),
                "org.h2.Driver"
        );
    }

    private DataSource createDataSource(String url, String username, String password, String driverClassName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(1);
        dataSource.setPoolName("InsightFlowPool");
        return dataSource;
    }

    private String inferDriver(String url) {
        if (url != null && url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        return "org.h2.Driver";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

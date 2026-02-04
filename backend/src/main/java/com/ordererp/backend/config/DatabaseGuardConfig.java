package com.ordererp.backend.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseGuardConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseGuardConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource, Environment env) {
        return flyway -> {
            logAndValidateDatabase(dataSource, env, "before flyway migrate");
            flyway.migrate();
        };
    }

    @Bean
    public ApplicationRunner databaseStartupLogger(DataSource dataSource, Environment env) {
        return args -> logAndValidateDatabase(dataSource, env, "after application start");
    }

    private static void logAndValidateDatabase(DataSource dataSource, Environment env, String phase) {
        String expected = env.getProperty("app.datasource.expected-database");
        boolean failOnMismatch = env.getProperty("app.datasource.fail-on-mismatch", Boolean.class, true);

        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            String user = conn.getMetaData().getUserName();
            String actualDb = safeCurrentDatabase(conn);

            log.info("Database connection {}: url='{}', user='{}', db='{}'", phase, url, user, actualDb);

            if (expected != null && !expected.isBlank() && actualDb != null && !actualDb.equalsIgnoreCase(expected)) {
                String msg = "Connected to unexpected database '" + actualDb + "' (expected '" + expected
                        + "'). Check spring.datasource.url / MYSQL_DB env var / IDE run args.";
                if (failOnMismatch) {
                    throw new IllegalStateException(msg);
                }
                log.warn(msg);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to validate database connection during " + phase + ": "
                    + ex.getMessage(), ex);
        }
    }

    private static String safeCurrentDatabase(Connection conn) {
        // MySQL：select database()
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select database()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (Exception ignored) {
            // 忽略异常，走后续兜底
        }

        // 通用兜底：使用 JDBC catalog
        try {
            return conn.getCatalog();
        } catch (Exception ignored) {
            return null;
        }
    }
}

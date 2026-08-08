package com.example.urlshorten.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Ensures the target PostgreSQL database exists before Hikari/JPA start.
 * Tables/columns are created by Hibernate ({@code spring.jpa.hibernate.ddl-auto=update}).
 */
public class PostgresDatabaseCreator implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(PostgresDatabaseCreator.class);
    private static final Pattern JDBC_URL = Pattern.compile(
            "^jdbc:postgresql://([^/?]+)(/([^/?]*))?([?;].*)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SAFE_DB_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        String jdbcUrl = env.getProperty("spring.datasource.url");
        if (jdbcUrl == null || !jdbcUrl.toLowerCase().startsWith("jdbc:postgresql:")) {
            return;
        }

        Matcher matcher = JDBC_URL.matcher(jdbcUrl);
        if (!matcher.matches()) {
            log.warn("Could not parse datasource URL for database auto-create: {}", jdbcUrl);
            return;
        }

        String hostPort = matcher.group(1);
        String database = matcher.group(3);
        if (database == null || database.isBlank() || "postgres".equals(database)) {
            return;
        }
        if (!SAFE_DB_NAME.matcher(database).matches()) {
            throw new IllegalStateException("Unsafe database name in datasource URL: " + database);
        }

        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "");
        String adminUrl = "jdbc:postgresql://" + hostPort + "/postgres";

        try {
            Class.forName("org.postgresql.Driver");
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                boolean exists;
                try (ResultSet rs = statement.executeQuery(
                        "SELECT 1 FROM pg_database WHERE datname = '" + database + "'")) {
                    exists = rs.next();
                }
                if (!exists) {
                    statement.executeUpdate("CREATE DATABASE " + database);
                    log.info("Created PostgreSQL database '{}'", database);
                } else {
                    log.debug("PostgreSQL database '{}' already exists", database);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to ensure PostgreSQL database '" + database + "' exists. "
                            + "Is Postgres running and are DB_USERNAME/DB_PASSWORD correct?",
                    ex
            );
        }
    }
}

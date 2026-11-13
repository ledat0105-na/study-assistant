package com.example.studyassistant.config;

import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentActivitySchema implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DocumentActivitySchema(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if ("MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                allowDocumentActivity(connection, "flashcards");
                allowDocumentActivity(connection, "quizzes");
            }
            return null;
        });
    }

    private void allowDocumentActivity(Connection connection, String table) throws SQLException {
        boolean required;
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, "topic_id")) {
            required = columns.next() && "NO".equals(columns.getString("IS_NULLABLE"));
        }
        if (required) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN topic_id BIGINT NULL");
            }
        }
    }
}

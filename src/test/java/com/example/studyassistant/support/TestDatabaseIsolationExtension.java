package com.example.studyassistant.support;

import java.sql.DriverManager;
import java.util.ArrayList;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ActiveProfiles;

/** Explicitly opted-in isolation of QA fixtures; never operates on an application database. */
public class TestDatabaseIsolationExtension implements BeforeAllCallback {
    @Override public void beforeAll(ExtensionContext context) throws Exception {
        if (!Boolean.getBoolean("qa.integration.reset-database")) return;
        ActiveProfiles profiles = AnnotatedElementUtils.findMergedAnnotation(context.getRequiredTestClass(), ActiveProfiles.class);
        if (profiles == null || java.util.Arrays.stream(profiles.value()).noneMatch("integration"::equals)) return;
        String username = System.getenv().getOrDefault("TEST_DB_USERNAME", "root");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/studyassistant_rag_test", username, password);
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT DATABASE()")) {
                if (!rows.next() || !"studyassistant_rag_test".equals(rows.getString(1))) {
                    throw new IllegalStateException("Refusing to reset a non-test database");
                }
            }
            var tables = new ArrayList<String>();
            try (var rows = statement.executeQuery("SHOW TABLES")) {
                while (rows.next()) {
                    String table = rows.getString(1);
                    if (!table.matches("[a-zA-Z0-9_]+")) throw new IllegalStateException("Unexpected table name");
                    tables.add(table);
                }
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            try { for (String table : tables) statement.execute("TRUNCATE TABLE `" + table + "`"); }
            finally { statement.execute("SET FOREIGN_KEY_CHECKS=1"); }
        }
    }
}

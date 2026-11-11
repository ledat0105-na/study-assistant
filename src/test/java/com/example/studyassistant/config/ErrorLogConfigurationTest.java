package com.example.studyassistant.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ErrorLogConfigurationTest {
    @TempDir Path directory;

    @Test void errorFileCapturesUtf8AndStackTraceButExcludesInfo() throws Exception {
        var context = new LoggerContext();
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        context.putProperty("APP_LOG_DIR", directory.toString().replace('\\', '/'));
        try {
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
            var log = context.getLogger("test.errors");
            log.info("NOT_AN_ERROR");
            log.error("Lỗi kiểm thử tiếng Việt", new IllegalStateException("test failure"));
            String text = Files.readString(directory.resolve("error.log"));
            assertThat(text).contains("Lỗi kiểm thử tiếng Việt", "IllegalStateException", "test failure")
                .doesNotContain("NOT_AN_ERROR");
        } finally {
            context.stop();
        }
    }
}

package com.example.studyassistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class LegacyJackson2CompatibilityTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper legacyMapper;

    @Test
    void testLegacyMapperInjected() {
        // A. Is injectable as: com.fasterxml.jackson.databind.ObjectMapper
        assertThat(legacyMapper).isNotNull();
    }

    @Test
    void testLocalDateTimeRoundtrip() throws Exception {
        // B. serializes/deserializes LocalDateTime successfully
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS); // JSON serialization truncates precision
        TestDto dto = new TestDto(now);

        String json = legacyMapper.writeValueAsString(dto);
        assertThat(json).contains("time");

        TestDto deserialized = legacyMapper.readValue(json, TestDto.class);
        assertThat(deserialized.getTime()).isEqualTo(now);
    }

    @Test
    void testNoOldJacksonConfig() {
        // C. does not require the removed old JacksonConfig
        assertThat(context.containsBean("jacksonConfig")).isFalse();
    }

    @Test
    void testNotPrimary() {
        // D. does not declare @Primary
        if (context instanceof GenericApplicationContext) {
            BeanDefinition bd = ((GenericApplicationContext) context)
                    .getBeanFactory().getBeanDefinition("legacyJackson2ObjectMapper");
            assertThat(bd.isPrimary()).isFalse();
        }
    }

    @Test
    void testSpringBootJackson3Preserved() {
        // E. does not disable Spring Boot's Jackson-3 web infrastructure
        try {
            Class<?> jackson3Class = Class.forName("tools.jackson.databind.ObjectMapper");
            String[] beans = context.getBeanNamesForType(jackson3Class);
            assertThat(beans.length).isGreaterThan(0);
        } catch (ClassNotFoundException e) {
            // If tools.jackson.databind.ObjectMapper is not in the classpath at all in this specific test environment
            System.out.println("tools.jackson.databind.ObjectMapper class not found in classpath.");
        }
    }

    static class TestDto {
        private LocalDateTime time;
        public TestDto() {}
        public TestDto(LocalDateTime time) { this.time = time; }
        public LocalDateTime getTime() { return time; }
        public void setTime(LocalDateTime time) { this.time = time; }
    }
}

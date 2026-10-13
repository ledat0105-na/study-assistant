package com.example.studyassistant.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UtcInstantAttributeConverterTest {

    private final UtcInstantAttributeConverter converter = new UtcInstantAttributeConverter();

    @Test
    void testConvertToDatabaseColumn() {
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        LocalDateTime ldt = converter.convertToDatabaseColumn(instant);
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0), ldt);
    }

    @Test
    void testConvertToEntityAttribute() {
        LocalDateTime ldt = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        Instant instant = converter.convertToEntityAttribute(ldt);
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), instant);
    }

    @Test
    void testMicrosecondsRoundTrip() {
        Instant instant = Instant.parse("2026-01-01T00:00:00.123456Z");
        LocalDateTime ldt = converter.convertToDatabaseColumn(instant);
        Instant back = converter.convertToEntityAttribute(ldt);
        assertEquals(instant, back);
    }
    
    @Test
    void testNulls() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}

package com.example.studyassistant.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HashUtilTest {
    @Test void bcryptUsesSaltAndAuthenticatesCorrectPassword() {
        String raw = "MatKhau123!";
        String first = HashUtil.hash(raw), second = HashUtil.hash(raw);
        assertThat(first).hasSize(60).isNotEqualTo(second);
        assertThat(HashUtil.verify(raw, first)).isTrue();
        assertThat(HashUtil.verify("wrong", first)).isFalse();
    }
    @Test void nullAndCorruptDatabaseValuesFailSafely() {
        assertThat(HashUtil.verify(null, "invalid")).isFalse();
        assertThat(HashUtil.verify("password", null)).isFalse();
        assertThat(HashUtil.verify("password", "broken-database-value")).isFalse();
    }
}

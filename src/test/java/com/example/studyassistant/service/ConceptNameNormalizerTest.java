package com.example.studyassistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConceptNameNormalizerTest {

    private ConceptNameNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ConceptNameNormalizer();
    }

    @Test
    void normalize_BasicRules() {
        assertThat(normalizer.normalize("Machine Learning")).isEqualTo("machine learning");
        assertThat(normalizer.normalize("  Machine   Learning  ")).isEqualTo("machine learning");
        assertThat(normalizer.normalize("MACHINE\tLEARNING")).isEqualTo("machine learning");
    }

    @Test
    void normalize_VietnameseAccentsPreserved() {
        // "Học Máy" must NOT become "hoc may"
        assertThat(normalizer.normalize("Học Máy")).isEqualTo("học máy");
        assertThat(normalizer.normalize(" TRÍ  TUỆ\nNhân Tạo ")).isEqualTo("trí tuệ nhân tạo");
    }

    @Test
    void normalize_PunctuationPreserved() {
        assertThat(normalizer.normalize("C++")).isEqualTo("c++");
        assertThat(normalizer.normalize("C#")).isEqualTo("c#");
        assertThat(normalizer.normalize("Node.js")).isEqualTo("node.js");
        assertThat(normalizer.normalize(".NET")).isEqualTo(".net");
        
        // Ensure distinct concepts remain distinct
        assertThat(normalizer.normalize("C")).isNotEqualTo(normalizer.normalize("C++"));
        assertThat(normalizer.normalize("C")).isNotEqualTo(normalizer.normalize("C#"));
    }

    @Test
    void normalize_RejectsNull() {
        assertThatThrownBy(() -> normalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void normalize_RejectsBlank() {
        assertThatThrownBy(() -> normalizer.normalize(""))
                .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> normalizer.normalize("   \t  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalize_PreservesCaseButFixesWhitespace() {
        assertThat(normalizer.canonicalize("  Machine    Learning  ")).isEqualTo("Machine Learning");
        assertThat(normalizer.canonicalize("MACHINE\tLEARNING")).isEqualTo("MACHINE LEARNING");
        assertThat(normalizer.canonicalize("C++")).isEqualTo("C++");
        assertThat(normalizer.canonicalize("Học Máy")).isEqualTo("Học Máy");
    }
}

package com.example.studyassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptivePolicyPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(AdaptivePolicyProperties.class)
    static class TestConfig {
    }

    @Test
    void testValidDefaultConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AdaptivePolicyProperties.class);
        });
    }

    @Test
    void testInvalidDefault25Max20_Fails() {
        contextRunner
                .withPropertyValues(
                        "studyassistant.adaptive.policy.v1.default-limit=25",
                        "studyassistant.adaptive.policy.v1.max-limit=20"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    // Message could be wrapped, so we just verify it failed validation
                });
    }

    @Test
    void testValidDefault10Max15_Succeeds() {
        contextRunner
                .withPropertyValues(
                        "studyassistant.adaptive.policy.v1.default-limit=10",
                        "studyassistant.adaptive.policy.v1.max-limit=15"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AdaptivePolicyProperties props = context.getBean(AdaptivePolicyProperties.class);
                    assertThat(props.getDefaultLimit()).isEqualTo(10);
                    assertThat(props.getMaxLimit()).isEqualTo(15);
                });
    }

    @Test
    void testInvalidThresholdBelowZero_Fails() {
        contextRunner
                .withPropertyValues("studyassistant.adaptive.policy.v1.mastered-threshold=-0.01")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void testInvalidThresholdAboveOne_Fails() {
        contextRunner
                .withPropertyValues("studyassistant.adaptive.policy.v1.mastered-threshold=1.01")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void testInvalidMinEvidenceZero_Fails() {
        contextRunner
                .withPropertyValues("studyassistant.adaptive.policy.v1.mastered-min-evidence=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void testInvalidDefaultLimitZero_Fails() {
        contextRunner
                .withPropertyValues("studyassistant.adaptive.policy.v1.default-limit=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void testInvalidMaxLimitZero_Fails() {
        contextRunner
                .withPropertyValues("studyassistant.adaptive.policy.v1.max-limit=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void testValidLimits1and1_Succeeds() {
        contextRunner
                .withPropertyValues(
                        "studyassistant.adaptive.policy.v1.default-limit=1",
                        "studyassistant.adaptive.policy.v1.max-limit=1"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AdaptivePolicyProperties props = context.getBean(AdaptivePolicyProperties.class);
                    assertThat(props.getDefaultLimit()).isEqualTo(1);
                    assertThat(props.getMaxLimit()).isEqualTo(1);
                });
    }

    @Test
    void testValidLimits20and20_Succeeds() {
        contextRunner
                .withPropertyValues(
                        "studyassistant.adaptive.policy.v1.default-limit=20",
                        "studyassistant.adaptive.policy.v1.max-limit=20"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AdaptivePolicyProperties props = context.getBean(AdaptivePolicyProperties.class);
                    assertThat(props.getDefaultLimit()).isEqualTo(20);
                    assertThat(props.getMaxLimit()).isEqualTo(20);
                });
    }
}

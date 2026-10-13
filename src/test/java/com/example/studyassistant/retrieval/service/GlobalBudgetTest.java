package com.example.studyassistant.retrieval.service;

import com.example.studyassistant.config.AiChatProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GlobalBudgetTest {

    @Test
    void testA_ValidConfigurationAccepted() {
        AiChatProperties props = new AiChatProperties();
        props.setModel("gpt-4o-mini");
        props.setContextWindowTokens(8000);
        props.setMaxOutputTokens(1000);
        props.setInputSafetyMarginTokens(500);
        props.validate(); // Should not throw
        assertThat(props.getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void testB_BlankModelRejected() {
        AiChatProperties props = new AiChatProperties();
        props.setModel("");
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testC_ContextWindowInvalidRejected() {
        AiChatProperties props = new AiChatProperties();
        props.setContextWindowTokens(0);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testD_MaxOutputInvalidRejected() {
        AiChatProperties props = new AiChatProperties();
        props.setMaxOutputTokens(-1);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testE_MarginInvalidRejected() {
        AiChatProperties props = new AiChatProperties();
        props.setInputSafetyMarginTokens(-10);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testF_OutputAndMarginConsumesContextRejected() {
        AiChatProperties props = new AiChatProperties();
        props.setContextWindowTokens(1000);
        props.setMaxOutputTokens(800);
        props.setInputSafetyMarginTokens(300);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testO_ConfigArithmeticOverflowPreventsBypass() {
        AiChatProperties props = new AiChatProperties();
        props.setContextWindowTokens(1000);
        props.setMaxOutputTokens(Integer.MAX_VALUE - 100);
        props.setInputSafetyMarginTokens(Integer.MAX_VALUE - 100);
        // This previously bypassed the check due to int underflow. Now it uses long.
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testP_InvalidHugeConfigCombinations() {
        AiChatProperties props = new AiChatProperties();
        props.setContextWindowTokens(Integer.MAX_VALUE);
        props.setMaxOutputTokens(Integer.MAX_VALUE);
        props.setInputSafetyMarginTokens(1000);
        assertThatThrownBy(props::validate).isInstanceOf(IllegalStateException.class);
    }
}

package com.example.studyassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LegacyJackson2Config {

    @Bean(name = "legacyJackson2ObjectMapper")
    public com.fasterxml.jackson.databind.ObjectMapper legacyJackson2ObjectMapper() {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}

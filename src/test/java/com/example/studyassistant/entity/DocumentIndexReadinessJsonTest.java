package com.example.studyassistant.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentIndexReadinessJsonTest {
    @Test void activeVersionIsReadableButCannotBeSetByIncomingJson() throws Exception {
        var mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        Document document = new Document(); document.setId(42L); document.setStatus("READY"); document.setActiveIndexVersion("index-v2");
        var json = mapper.readTree(mapper.writeValueAsString(document));
        assertThat(json.path("activeIndexVersion").asText()).isEqualTo("index-v2");
        Document incoming = mapper.readValue("{\"status\":\"READY\",\"activeIndexVersion\":\"forged-version\"}",Document.class);
        assertThat(incoming.getActiveIndexVersion()).isNull();
    }

    @Test void bootJackson3AlsoExposesOnlyReadAccessToActiveVersion() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        Document document = new Document(); document.setId(42L); document.setActiveIndexVersion("index-v2");
        assertThat(mapper.readTree(mapper.writeValueAsString(document)).path("activeIndexVersion").asText()).isEqualTo("index-v2");
        assertThat(mapper.readValue("{\"activeIndexVersion\":\"forged\"}",Document.class).getActiveIndexVersion()).isNull();
    }
}

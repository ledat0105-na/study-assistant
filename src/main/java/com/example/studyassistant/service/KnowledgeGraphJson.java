package com.example.studyassistant.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;

final class KnowledgeGraphJson {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private KnowledgeGraphJson() {}
    static <T> T envelope(String json, String field, Class<T> type) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.isObject() || root.size() != 1 || !root.has(field)
                || !root.get(field).isArray()) {
            throw new IllegalArgumentException("Required graph array missing or invalid");
        }
        for (JsonNode candidate : root.get(field)) {
            if (!candidate.isObject()) throw new IllegalArgumentException("Invalid graph candidate shape");
            var fields = candidate.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isNull()) continue; // semantic validation handles required values
                boolean valid = switch (entry.getKey()) {
                    case "confidence" -> value.isNumber();
                    case "sourceConceptId", "targetConceptId" -> value.isIntegralNumber() && value.canConvertToLong();
                    case "name", "description", "conceptType", "evidenceText", "relationType" -> value.isTextual();
                    default -> false;
                };
                if (!valid) throw new IllegalArgumentException("Invalid graph field type");
            }
        }
        return MAPPER.treeToValue(root, type);
    }
}

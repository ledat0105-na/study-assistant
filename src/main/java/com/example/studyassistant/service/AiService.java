package com.example.studyassistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    public static class NoApiKeyException extends RuntimeException {}

    public boolean hasApiKey() {
        return openaiApiKey != null && !openaiApiKey.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    public String callOpenAI(String prompt) throws Exception {
        return callOpenAI(prompt, false);
    }

    @SuppressWarnings("unchecked")
    public String callOpenAI(String prompt, boolean jsonMode) throws Exception {
        if (!hasApiKey()) {
            throw new NoApiKeyException();
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o-mini");
        requestBody.put("max_tokens", 6000);
        if (jsonMode) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, String> messageObj = new HashMap<>();
        messageObj.put("role", "user");
        messageObj.put("content", prompt);

        requestBody.put("messages", Collections.singletonList(messageObj));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);

        if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
            Map<String, Object> body = responseEntity.getBody();
            List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> messageMap = (Map<String, Object>) choice.get("message");
                String finishReason = String.valueOf(choice.get("finish_reason"));
                if (messageMap != null) {
                    String content = messageMap.get("content").toString();
                    if ("length".equals(finishReason)) {
                        throw new IllegalStateException("Phản hồi của AI bị cắt do vượt giới hạn độ dài (thử lại hoặc rút gọn tài liệu)");
                    }
                    return content;
                }
            }
        }
        throw new IllegalStateException("Failed to get valid response from OpenAI API");
    }
}

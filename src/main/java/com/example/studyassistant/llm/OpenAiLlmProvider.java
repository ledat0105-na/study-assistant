package com.example.studyassistant.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.studyassistant.config.AiChatProperties;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OpenAiLlmProvider implements LlmProvider {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Autowired(required = false)
    private AiChatProperties aiChatProperties;

    private final RestTemplate restTemplate;

    public OpenAiLlmProvider() {
        this.restTemplate = new RestTemplate();
    }

    public OpenAiLlmProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String generate(String systemPrompt, String userPrompt, double temperature) {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        String model = (aiChatProperties != null && aiChatProperties.getModel() != null) 
                ? aiChatProperties.getModel() : "gpt-4o-mini";
        int maxTokens = (aiChatProperties != null && aiChatProperties.getMaxOutputTokens() > 0)
                ? aiChatProperties.getMaxOutputTokens() : 1000;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);

        List<Map<String, String>> messages = new ArrayList<>();
        
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            Map<String, String> sysMsg = new HashMap<>();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.add(sysMsg);
        }
        
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        requestBody.put("messages", messages);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                Map<String, Object> body = responseEntity.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> messageMap = (Map<String, Object>) choice.get("message");
                    String finishReason = String.valueOf(choice.get("finish_reason"));
                    
                    if (messageMap != null && messageMap.containsKey("content")) {
                        if ("length".equals(finishReason)) {
                            throw new RuntimeException("LLM response truncated due to length");
                        }
                        return messageMap.get("content").toString();
                    }
                }
            }
            throw new RuntimeException("Failed to get valid response from OpenAI API");
        } catch (Exception e) {
            throw new RuntimeException("LLM Provider failure", e);
        }
    }
}

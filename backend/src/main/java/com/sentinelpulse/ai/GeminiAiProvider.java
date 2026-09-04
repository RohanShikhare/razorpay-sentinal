package com.sentinelpulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelpulse.config.AiProperties;
import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.exception.SentinelAiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Live REST AI Provider for Google Gemini API.
 * Configured via AiProperties without requiring external SDK dependencies.
 */
@Component("geminiAiProvider")
public class GeminiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiAiProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));

        this.restClient = RestClient.builder()
                .baseUrl(GEMINI_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String getProviderName() {
        return "GEMINI";
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

    @Override
    public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new SentinelAiException("AI API key is not configured for provider GEMINI");
        }

        try {
            String evidenceJson = objectMapper.writeValueAsString(evidence);

            ObjectNode requestBody = objectMapper.createObjectNode();

            // System instruction
            ObjectNode sysInst = requestBody.putObject("system_instruction");
            ArrayNode sysParts = sysInst.putArray("parts");
            sysParts.addObject().put("text", systemInstruction);

            // User message with evidence
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            ArrayNode userParts = userContent.putArray("parts");
            userParts.addObject().put("text", "EVIDENCE_DATA:\n" + evidenceJson);

            // Generation config with JSON output enforcement
            ObjectNode genConfig = requestBody.putObject("generationConfig");
            genConfig.put("temperature", properties.getTemperature());
            genConfig.put("response_mime_type", "application/json");

            String url = String.format("/v1beta/models/%s:generateContent?key=%s", properties.getModel(), apiKey);

            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new SentinelAiException("Received empty response from Gemini API");
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                throw new SentinelAiException("No candidates returned in Gemini API response");
            }

            JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new SentinelAiException("No text content found in Gemini candidate response");
            }

            return cleanJsonOutput(textNode.asText());
        } catch (SentinelAiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute Gemini AI diagnosis request: {}", e.getMessage());
            throw new SentinelAiException("Gemini AI diagnosis request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateInvestigationJson(AiEvidenceDto evidence, String diagnosisSummary, String systemInstruction) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new SentinelAiException("AI API key is not configured for provider GEMINI");
        }

        try {
            String evidenceJson = objectMapper.writeValueAsString(evidence);

            ObjectNode requestBody = objectMapper.createObjectNode();

            // System instruction
            ObjectNode sysInst = requestBody.putObject("system_instruction");
            ArrayNode sysParts = sysInst.putArray("parts");
            sysParts.addObject().put("text", systemInstruction);

            // User message with evidence and optional prior diagnosis
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            ArrayNode userParts = userContent.putArray("parts");

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("EVIDENCE_DATA:\n").append(evidenceJson).append("\n\n");
            if (diagnosisSummary != null && !diagnosisSummary.isBlank()) {
                promptBuilder.append("PRIOR_DIAGNOSIS_SUMMARY:\n").append(diagnosisSummary).append("\n\n");
            }
            promptBuilder.append("Please perform deep root-cause investigation and compare competing hypotheses.");

            userParts.addObject().put("text", promptBuilder.toString());

            // Generation config with JSON output enforcement
            ObjectNode genConfig = requestBody.putObject("generationConfig");
            genConfig.put("temperature", properties.getTemperature());
            genConfig.put("response_mime_type", "application/json");

            String url = String.format("/v1beta/models/%s:generateContent?key=%s", properties.getModel(), apiKey);

            String responseBody = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new SentinelAiException("Received empty response from Gemini API");
            }

            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                throw new SentinelAiException("No candidates returned in Gemini API response");
            }

            JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new SentinelAiException("No text content found in Gemini candidate response");
            }

            return cleanJsonOutput(textNode.asText());
        } catch (SentinelAiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute Gemini AI investigation request: {}", e.getMessage());
            throw new SentinelAiException("Gemini AI investigation request failed: " + e.getMessage(), e);
        }
    }

    private String cleanJsonOutput(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}

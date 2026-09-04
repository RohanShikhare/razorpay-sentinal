package com.sentinelpulse.ai;

import com.sentinelpulse.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting the active AI provider based on configuration.
 */
@Component
public class AiProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(AiProviderFactory.class);

    private final AiProperties properties;
    private final MockAiProvider mockAiProvider;
    private final GeminiAiProvider geminiAiProvider;

    public AiProviderFactory(AiProperties properties, MockAiProvider mockAiProvider, GeminiAiProvider geminiAiProvider) {
        this.properties = properties;
        this.mockAiProvider = mockAiProvider;
        this.geminiAiProvider = geminiAiProvider;
    }

    public AiProvider getProvider() {
        String configured = properties.getProvider() != null ? properties.getProvider().trim().toLowerCase() : "mock";

        if ("gemini".equals(configured) || "live".equals(configured)) {
            if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                log.warn("AI provider is configured as '{}' but no API key was provided. Falling back to MOCK provider.", configured);
                return mockAiProvider;
            }
            return geminiAiProvider;
        }

        return mockAiProvider;
    }
}

package com.sentinelpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the AI Diagnostic Agent.
 */
@Configuration
@ConfigurationProperties(prefix = "sentinel.ai")
public class AiProperties {

    /**
     * AI Provider type: "mock", "gemini", or "custom".
     */
    private String provider = "mock";

    /**
     * API key for the live AI provider (e.g. Gemini).
     */
    private String apiKey = "";

    /**
     * Model name/identifier (e.g. "gemini-1.5-flash").
     */
    private String model = "gemini-1.5-flash";

    /**
     * LLM sampling temperature (0.0 to 1.0).
     */
    private double temperature = 0.2;

    /**
     * HTTP connection and read timeout in milliseconds.
     */
    private int timeoutMs = 10000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

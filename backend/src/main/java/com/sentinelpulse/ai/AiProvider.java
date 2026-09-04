package com.sentinelpulse.ai;

import com.sentinelpulse.dto.AiEvidenceDto;

/**
 * Pluggable provider interface for invoking AI diagnosis against structured incident evidence.
 */
public interface AiProvider {

    /**
     * Name identifier of this provider (e.g. "MOCK", "GEMINI").
     */
    String getProviderName();

    /**
     * Active model name (e.g. "mock-agent-v1", "gemini-1.5-flash").
     */
    String getModelName();

    /**
     * Analyzes the supplied structured evidence and returns the raw JSON diagnosis response.
     *
     * @param evidence          structured deterministic evidence
     * @param systemInstruction prompt guidelines and constraints
     * @return raw JSON string from the AI provider
     */
    String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction);
}

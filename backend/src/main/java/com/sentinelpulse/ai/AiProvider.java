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

    /**
     * Conducts a deep root-cause investigation by evaluating competing hypotheses against
     * deterministic evidence and returns the raw JSON investigation response.
     *
     * @param evidence          structured deterministic evidence
     * @param diagnosisSummary  summary of prior Module 6 diagnosis if available
     * @param systemInstruction prompt guidelines and constraints
     * @return raw JSON string from the AI provider
     */
    default String generateInvestigationJson(AiEvidenceDto evidence, String diagnosisSummary, String systemInstruction) {
        return generateDiagnosisJson(evidence, systemInstruction);
    }

    /**
     * Synthesizes a structured recovery strategy proposal evaluating applicable strategies,
     * bounds, stopping conditions, and alternatives against deterministic evidence and investigation findings.
     *
     * @param evidence             structured deterministic evidence
     * @param diagnosisSummary     summary of prior Module 6 diagnosis if available
     * @param investigationSummary summary of prior Module 7 investigation if available
     * @param systemInstruction    prompt guidelines and constraints
     * @return raw JSON string from the AI provider
     */
    default String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
        return generateDiagnosisJson(evidence, systemInstruction);
    }
}

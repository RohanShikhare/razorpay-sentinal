package com.sentinelpulse.dto;

import java.math.BigDecimal;

/**
 * Response body for GET /api/v1/incidents/{id}/financial-impact.
 *
 * <h2>Formulas</h2>
 * <ul>
 *   <li>{@code affectedTransactionValue} = sum of amount over every payment
 *       in the incident's affected population (see {@code FinancialImpactService}
 *       for how that population is reconstructed from {@code targetBank}/
 *       {@code targetPaymentMethod}/{@code targetMerchantId} + the incident's
 *       start time).</li>
 *   <li>{@code failedTransactionValue} = sum of amount over the FAILED
 *       subset of that population - the simple "money attached to a failed
 *       transaction" figure.</li>
 *   <li>{@code revenueAtRisk} = the value already computed by the
 *       detection engine at (re)detection time: {@code failedTransactionValue
 *       × max(0, (currentFailureRate − baselineFailureRate) / currentFailureRate)}
 *       - i.e. only the *excess* failed value attributable to the incident,
 *       not baseline "normal" failures. This engine reads it rather than
 *       recomputing it, which is what makes it a stable, explainable
 *       snapshot even if live payment data changes later (e.g.
 *       recovery simulation, or a simulation reset).</li>
 *   <li>{@code estimatedRecoverableRevenue} = {@code revenueAtRisk} ×
 *       (value-weighted average recoverability rate across the failed
 *       population's actual error-code mix). See {@code RecoverabilityClassifier}
 *       and {@code RecoverabilityProperties}.</li>
 *   <li>{@code nonRecoverableRevenue} = {@code revenueAtRisk} −
 *       {@code estimatedRecoverableRevenue} (by subtraction, so the two
 *       always sum back to revenueAtRisk exactly).</li>
 *   <li>{@code averageTransactionValue} = {@code affectedTransactionValue}
 *       / {@code affectedTransactions} (0 if there are no affected
 *       transactions).</li>
 *   <li>{@code recoverablePercentage} = {@code estimatedRecoverableRevenue}
 *       / {@code revenueAtRisk} × 100 (0 if revenueAtRisk is 0).</li>
 * </ul>
 */
public record FinancialImpactResponse(
        Long incidentId,
        long affectedTransactions,
        BigDecimal affectedTransactionValue,
        BigDecimal failedTransactionValue,
        BigDecimal revenueAtRisk,
        BigDecimal estimatedRecoverableRevenue,
        BigDecimal nonRecoverableRevenue,
        BigDecimal averageTransactionValue,
        BigDecimal largestAffectedTransaction,
        double recoverablePercentage
) {
}

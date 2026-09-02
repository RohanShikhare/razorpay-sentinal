package com.sentinelpulse.financial;

import com.sentinelpulse.entity.ErrorCode;

import java.util.Map;

/**
 * Deterministic, fixed classification of which {@link ErrorCode}s are
 * plausibly recoverable. This is business domain knowledge (which failure
 * reasons tend to be transient vs. terminal), so it lives in code rather
 * than configuration - but the *numeric rate* applied per level is
 * configurable (see {@code RecoverabilityProperties}), so the classification
 * itself can stay stable while the weighting is tunable.
 *
 * <ul>
 *   <li>{@code BANK_TIMEOUT}, {@code NETWORK_ERROR}, {@code RATE_LIMIT} -
 *       transient infrastructure/rail issues, often resolved by a retry.
 *       Classified {@link RecoverabilityLevel#HIGH_RECOVERABILITY}.</li>
 *   <li>{@code ISSUER_DECLINED} - the issuing bank actively refused the
 *       transaction; sometimes retryable (e.g. temporary risk hold), often
 *       not. Classified {@link RecoverabilityLevel#MEDIUM_RECOVERABILITY}.</li>
 *   <li>{@code INSUFFICIENT_FUNDS} - a customer-side condition unlikely to
 *       resolve on an immediate retry. Classified
 *       {@link RecoverabilityLevel#LOW_RECOVERABILITY}.</li>
 *   <li>{@code UNKNOWN} (and any unmapped code) - insufficient information
 *       to judge either way. Classified {@link RecoverabilityLevel#UNKNOWN}.</li>
 * </ul>
 */
public final class RecoverabilityClassifier {

    private static final Map<ErrorCode, RecoverabilityLevel> CLASSIFICATION = Map.of(
            ErrorCode.BANK_TIMEOUT, RecoverabilityLevel.HIGH_RECOVERABILITY,
            ErrorCode.NETWORK_ERROR, RecoverabilityLevel.HIGH_RECOVERABILITY,
            ErrorCode.RATE_LIMIT, RecoverabilityLevel.HIGH_RECOVERABILITY,
            ErrorCode.ISSUER_DECLINED, RecoverabilityLevel.MEDIUM_RECOVERABILITY,
            ErrorCode.INSUFFICIENT_FUNDS, RecoverabilityLevel.LOW_RECOVERABILITY,
            ErrorCode.UNKNOWN, RecoverabilityLevel.UNKNOWN,
            ErrorCode.NONE, RecoverabilityLevel.UNKNOWN
    );

    private RecoverabilityClassifier() {
    }

    public static RecoverabilityLevel classify(ErrorCode errorCode) {
        return CLASSIFICATION.getOrDefault(errorCode, RecoverabilityLevel.UNKNOWN);
    }
}

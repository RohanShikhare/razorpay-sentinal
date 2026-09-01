package com.sentinelpulse.repository;

import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Composable {@link Specification} builders for filtering payments.
 *
 * Each filter is optional (a null value means "don't filter on this") so
 * callers - the REST layer today, the Incident Simulator or monitoring
 * engine later - can combine any subset of them by chaining {@code and()}.
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasPaymentMethod(PaymentMethod paymentMethod) {
        return (root, query, cb) -> paymentMethod == null ? null : cb.equal(root.get("paymentMethod"), paymentMethod);
    }

    public static Specification<Payment> hasBank(String bank) {
        return (root, query, cb) -> (bank == null || bank.isBlank()) ? null : cb.equal(root.get("bank"), bank);
    }

    public static Specification<Payment> hasErrorCode(ErrorCode errorCode) {
        return (root, query, cb) -> errorCode == null ? null : cb.equal(root.get("errorCode"), errorCode);
    }

    public static Specification<Payment> hasMerchantId(String merchantId) {
        return (root, query, cb) -> (merchantId == null || merchantId.isBlank())
                ? null : cb.equal(root.get("merchantId"), merchantId);
    }

    public static Specification<Payment> timestampFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<Payment> timestampTo(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    /**
     * Combines every filter that is non-null into one specification.
     * Any argument may be null to skip that filter.
     */
    public static Specification<Payment> filter(PaymentStatus status, PaymentMethod paymentMethod,
                                                  String bank, ErrorCode errorCode,
                                                  Instant from, Instant to) {
        return Specification.where(hasStatus(status))
                .and(hasPaymentMethod(paymentMethod))
                .and(hasBank(bank))
                .and(hasErrorCode(errorCode))
                .and(timestampFrom(from))
                .and(timestampTo(to));
    }
}

package com.sentinelpulse.dto;

import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API representation of a {@link Payment}, kept separate from the entity so
 * the wire format can evolve independently of the persistence model.
 */
public record PaymentDto(
        Long id,
        String merchantId,
        String customerId,
        BigDecimal amount,
        String currency,
        Instant timestamp,
        PaymentMethod paymentMethod,
        String bank,
        PaymentStatus status,
        ErrorCode errorCode
) {
    public static PaymentDto from(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getMerchantId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getTimestamp(),
                payment.getPaymentMethod(),
                payment.getBank(),
                payment.getStatus(),
                payment.getErrorCode()
        );
    }
}

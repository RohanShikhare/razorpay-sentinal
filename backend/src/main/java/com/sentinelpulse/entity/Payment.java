package com.sentinelpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A single payment transaction, synthetic or otherwise.
 *
 * This is the shared data foundation: incident
 * detection, revenue-at-risk, AI diagnosis, and recovery all read and
 * act on rows in this table. Indexes are declared on the
 * columns queries filter by most - status, bank, payment
 * method, error code, and time - so those queries stay cheap as the
 * dataset grows.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_bank", columnList = "bank"),
                @Index(name = "idx_payments_payment_method", columnList = "payment_method"),
                @Index(name = "idx_payments_error_code", columnList = "error_code"),
                @Index(name = "idx_payments_timestamp", columnList = "transaction_timestamp")
        }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_timestamp", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 16)
    private String bank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", nullable = false, length = 32)
    private ErrorCode errorCode;

    protected Payment() {
        // required by JPA
    }

    public Payment(String merchantId, String customerId, BigDecimal amount, String currency,
                   Instant timestamp, PaymentMethod paymentMethod, String bank,
                   PaymentStatus status, ErrorCode errorCode) {
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
        this.paymentMethod = paymentMethod;
        this.bank = bank;
        this.status = status;
        this.errorCode = errorCode;
    }

    public Long getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getBank() {
        return bank;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment payment)) return false;
        return id != null && id.equals(payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

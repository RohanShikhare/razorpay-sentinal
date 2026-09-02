package com.sentinelpulse.repository;

import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

/**
 * Persistence for {@link Payment}.
 *
 * Extends {@link JpaSpecificationExecutor} so callers can combine filters
 * (status, payment method, bank, error code, time window) freely via
 * {@link PaymentSpecifications} without a bespoke query per combination -
 * this is what lets later modules (e.g. the Incident Simulator) query
 * arbitrary subsets of payments without changing this repository.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    /**
     * Row counts grouped by status. Each result is {@code [PaymentStatus, Long]}.
     */
    @Query("select p.status, count(p) from Payment p group by p.status")
    List<Object[]> countGroupedByStatus();

    /**
     * Summed amounts grouped by status. Each result is {@code [PaymentStatus, BigDecimal]}.
     */
    @Query("select p.status, sum(p.amount) from Payment p group by p.status")
    List<Object[]> sumAmountGroupedByStatus();

    /**
     * Row counts and summed amounts grouped by payment method.
     * Each result is {@code [PaymentMethod, Long, BigDecimal]}.
     */
    @Query("select p.paymentMethod, count(p), sum(p.amount) from Payment p group by p.paymentMethod")
    List<Object[]> aggregateByPaymentMethod();

    /**
     * Row counts and summed amounts grouped by bank.
     * Each result is {@code [String, Long, BigDecimal]}.
     */
    @Query("select p.bank, count(p), sum(p.amount) from Payment p group by p.bank")
    List<Object[]> aggregateByBank();

    /**
     * Row counts and summed amounts of FAILED payments only, grouped by
     * payment method. Combined with {@link #aggregateByPaymentMethod()} in
     * {@code PaymentService} to expose a failure rate per segment - used by
     * the Module 5 dashboard's payment-method breakdown.
     * Each result is {@code [PaymentMethod, Long, BigDecimal]}.
     */
    @Query("select p.paymentMethod, count(p), sum(p.amount) from Payment p where p.status = com.sentinelpulse.entity.PaymentStatus.FAILED group by p.paymentMethod")
    List<Object[]> aggregateFailedByPaymentMethod();

    /**
     * Row counts and summed amounts of FAILED payments only, grouped by bank.
     * Each result is {@code [String, Long, BigDecimal]}.
     */
    @Query("select p.bank, count(p), sum(p.amount) from Payment p where p.status = com.sentinelpulse.entity.PaymentStatus.FAILED group by p.bank")
    List<Object[]> aggregateFailedByBank();

    /**
     * Row counts and summed amounts grouped by error code, excluding
     * {@link ErrorCode#NONE} - every row here is inherently a FAILED
     * payment, since only failures carry a real error code.
     * Each result is {@code [ErrorCode, Long, BigDecimal]}.
     */
    @Query("select p.errorCode, count(p), sum(p.amount) from Payment p where p.errorCode <> com.sentinelpulse.entity.ErrorCode.NONE group by p.errorCode")
    List<Object[]> aggregateByErrorCodeExcludingNone();

    /**
     * Total value of every payment in the table, regardless of status.
     */
    @Query("select sum(p.amount) from Payment p")
    BigDecimal sumAllAmount();

    /**
     * Merchant IDs ranked by transaction volume, highest first. Used by the
     * Incident Simulator to pick a merchant with enough volume for
     * {@code MERCHANT_SPECIFIC_DEGRADATION} to be statistically meaningful.
     * Each result is {@code [String merchantId, Long count]}.
     */
    @Query("select p.merchantId, count(p) from Payment p group by p.merchantId order by count(p) desc")
    List<Object[]> merchantVolumeRanking();
}

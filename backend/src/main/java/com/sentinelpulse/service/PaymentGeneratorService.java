package com.sentinelpulse.service;

import com.sentinelpulse.entity.Banks;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Produces a realistic synthetic payment dataset.
 *
 * "Realistic" here means: a healthy baseline. Payment methods, banks,
 * statuses, and error codes are all drawn from weighted distributions
 * rather than being uniform or forced, so the resulting dataset does not
 * already look like an incident - that manipulation is Module 2's job
 * (Incident Simulator), not this generator's.
 */
@Service
public class PaymentGeneratorService {

    private static final List<Weighted<PaymentMethod>> PAYMENT_METHOD_WEIGHTS = List.of(
            new Weighted<>(PaymentMethod.UPI, 0.45),
            new Weighted<>(PaymentMethod.CARD, 0.30),
            new Weighted<>(PaymentMethod.NETBANKING, 0.15),
            new Weighted<>(PaymentMethod.WALLET, 0.10)
    );

    private static final List<Weighted<String>> BANK_WEIGHTS = List.of(
            new Weighted<>(Banks.BANK_A, 0.30),
            new Weighted<>(Banks.BANK_B, 0.25),
            new Weighted<>(Banks.BANK_C, 0.20),
            new Weighted<>(Banks.BANK_X, 0.15),
            new Weighted<>(Banks.BANK_Y, 0.10)
    );

    private static final List<Weighted<PaymentStatus>> STATUS_WEIGHTS = List.of(
            new Weighted<>(PaymentStatus.SUCCESS, 0.94),
            new Weighted<>(PaymentStatus.FAILED, 0.05),
            new Weighted<>(PaymentStatus.PENDING, 0.01)
    );

    private static final List<Weighted<ErrorCode>> ERROR_CODE_WEIGHTS = List.of(
            new Weighted<>(ErrorCode.BANK_TIMEOUT, 0.30),
            new Weighted<>(ErrorCode.ISSUER_DECLINED, 0.25),
            new Weighted<>(ErrorCode.INSUFFICIENT_FUNDS, 0.20),
            new Weighted<>(ErrorCode.NETWORK_ERROR, 0.15),
            new Weighted<>(ErrorCode.RATE_LIMIT, 0.07),
            new Weighted<>(ErrorCode.UNKNOWN, 0.03)
    );

    private static final int MERCHANT_POOL_SIZE = 25;
    private static final int CUSTOMER_POOL_SIZE = 20_000;
    private static final String CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final int generationWindowHours;

    public PaymentGeneratorService(
            PaymentRepository paymentRepository,
            @Value("${sentinel.payments.generation-window-hours:48}") int generationWindowHours
    ) {
        this.paymentRepository = paymentRepository;
        this.generationWindowHours = generationWindowHours;
    }

    /**
     * Generates {@code count} synthetic payments and persists them.
     *
     * @param count number of payments to generate (already validated by the caller)
     * @param seed  optional seed for reproducible generation; a random seed is used if null
     * @return the payments that were persisted
     */
    @Transactional
    public List<Payment> generateAndPersist(int count, Long seed) {
        List<Payment> payments = generate(count, seed);
        return paymentRepository.saveAll(payments);
    }

    /**
     * Builds {@code count} synthetic payments in memory without touching the
     * database. Split out from {@link #generateAndPersist} so the
     * distribution logic can be tested directly, and so later modules can
     * reuse it to build payments without necessarily persisting them.
     */
    public List<Payment> generate(int count, Long seed) {
        Random random = seed != null ? new Random(seed) : new Random();

        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(generationWindowHours, ChronoUnit.HOURS);
        long windowSeconds = generationWindowHours * 3600L;

        List<Payment> payments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            payments.add(generateOne(random, windowStart, windowSeconds));
        }
        return payments;
    }

    public int getGenerationWindowHours() {
        return generationWindowHours;
    }

    private Payment generateOne(Random random, Instant windowStart, long windowSeconds) {
        String merchantId = String.format("merchant_%03d", 1 + random.nextInt(MERCHANT_POOL_SIZE));
        String customerId = String.format("customer_%05d", 1 + random.nextInt(CUSTOMER_POOL_SIZE));

        BigDecimal amount = randomAmount(random);
        Instant timestamp = windowStart.plusSeconds((long) (random.nextDouble() * windowSeconds));
        PaymentMethod paymentMethod = pickWeighted(random, PAYMENT_METHOD_WEIGHTS);
        String bank = pickWeighted(random, BANK_WEIGHTS);
        PaymentStatus status = pickWeighted(random, STATUS_WEIGHTS);

        // Never force a failure reason onto a payment that didn't fail.
        ErrorCode errorCode = status == PaymentStatus.FAILED
                ? pickWeighted(random, ERROR_CODE_WEIGHTS)
                : ErrorCode.NONE;

        return new Payment(merchantId, customerId, amount, CURRENCY, timestamp, paymentMethod, bank, status, errorCode);
    }

    /**
     * Skewed toward small/medium purchases with occasional high-value ones,
     * which is how real payment volume tends to distribute.
     */
    private BigDecimal randomAmount(Random random) {
        double roll = random.nextDouble();
        double value;
        if (roll < 0.70) {
            value = 50 + random.nextDouble() * (2_000 - 50);
        } else if (roll < 0.95) {
            value = 2_000 + random.nextDouble() * (20_000 - 2_000);
        } else {
            value = 20_000 + random.nextDouble() * (150_000 - 20_000);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> T pickWeighted(Random random, List<Weighted<T>> weights) {
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (Weighted<T> entry : weights) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry.value();
            }
        }
        // Guards against floating-point rounding leaving a tiny gap at the top.
        return weights.get(weights.size() - 1).value();
    }

    private record Weighted<T>(T value, double weight) {
    }
}

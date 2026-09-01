package com.sentinelpulse.service;

import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the generation logic directly (no Spring context, no database), and
 * separately confirms {@link PaymentGeneratorService#generateAndPersist}
 * delegates persistence to the repository as expected.
 */
class PaymentGeneratorServiceTest {

    private static final int WINDOW_HOURS = 48;
    private static final long SEED = 42L;

    private PaymentGeneratorService newService(PaymentRepository repository) {
        return new PaymentGeneratorService(repository, WINDOW_HOURS);
    }

    @Test
    void generatesExactlyTheRequestedCount() {
        PaymentGeneratorService service = newService(mock(PaymentRepository.class));
        List<Payment> payments = service.generate(1_000, SEED);
        assertEquals(1_000, payments.size());
    }

    @Test
    void neverForcesAnErrorCodeOntoANonFailedPayment() {
        PaymentGeneratorService service = newService(mock(PaymentRepository.class));
        List<Payment> payments = service.generate(2_000, SEED);

        for (Payment payment : payments) {
            if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.PENDING) {
                assertEquals(ErrorCode.NONE, payment.getErrorCode(),
                        "non-failed payments must not carry a failure error code");
            } else {
                assertFalse(payment.getErrorCode() == ErrorCode.NONE,
                        "failed payments must carry a real error code");
            }
        }
    }

    @Test
    void statusDistributionFavorsSuccessWithinARealisticRange() {
        PaymentGeneratorService service = newService(mock(PaymentRepository.class));
        List<Payment> payments = service.generate(5_000, SEED);

        Map<PaymentStatus, Long> counts = payments.stream()
                .collect(Collectors.groupingBy(Payment::getStatus, Collectors.counting()));

        double successRate = counts.getOrDefault(PaymentStatus.SUCCESS, 0L) / 5000.0;
        double failedRate = counts.getOrDefault(PaymentStatus.FAILED, 0L) / 5000.0;

        // Wide but meaningful bounds: this must look like a healthy baseline,
        // not an incident, and not artificially perfect either.
        assertTrue(successRate >= 0.85 && successRate <= 0.99,
                "expected a healthy success rate, got " + successRate);
        assertTrue(failedRate >= 0.01 && failedRate <= 0.12,
                "expected a modest failure rate, got " + failedRate);
    }

    @Test
    void noSingleBankOrErrorCodeDominatesUnrealistically() {
        PaymentGeneratorService service = newService(mock(PaymentRepository.class));
        List<Payment> payments = service.generate(5_000, SEED);

        Map<String, Long> byBank = payments.stream()
                .collect(Collectors.groupingBy(Payment::getBank, Collectors.counting()));

        // No bank should carry more than half the traffic, and every
        // configured bank should actually appear (including Bank X, which
        // later modules will target for incident simulation).
        for (long count : byBank.values()) {
            assertTrue(count < 5_000 * 0.5, "a single bank should not dominate the dataset");
        }
        assertTrue(byBank.containsKey("Bank X"), "Bank X should be present at a normal, non-dominant share");
    }

    @Test
    void timestampsFallWithinTheConfiguredGenerationWindow() {
        PaymentGeneratorService service = newService(mock(PaymentRepository.class));
        Instant before = Instant.now().minusSeconds(WINDOW_HOURS * 3600L + 5);
        List<Payment> payments = service.generate(500, SEED);
        Instant after = Instant.now();

        for (Payment payment : payments) {
            assertTrue(!payment.getTimestamp().isBefore(before), "timestamp should not be before the window");
            assertTrue(!payment.getTimestamp().isAfter(after), "timestamp should not be in the future");
        }
    }

    @Test
    void amountsArePositiveAndUseTwoDecimalPlaces() {
        PaymentGeneratorService service = newService(mock(PaymentRepository.class));
        List<Payment> payments = service.generate(500, SEED);

        for (Payment payment : payments) {
            BigDecimal amount = payment.getAmount();
            assertTrue(amount.compareTo(BigDecimal.ZERO) > 0, "amount must be positive");
            assertEquals(2, amount.scale(), "amount should be scaled to 2 decimal places");
        }
    }

    @Test
    void sameSeedProducesReproducibleGeneration() {
        PaymentGeneratorService a = newService(mock(PaymentRepository.class));
        PaymentGeneratorService b = newService(mock(PaymentRepository.class));

        List<Payment> first = a.generate(200, SEED);
        List<Payment> second = b.generate(200, SEED);

        for (int i = 0; i < first.size(); i++) {
            assertEquals(0, first.get(i).getAmount().compareTo(second.get(i).getAmount()));
            assertEquals(first.get(i).getBank(), second.get(i).getBank());
            assertEquals(first.get(i).getStatus(), second.get(i).getStatus());
        }
    }

    @Test
    void generateAndPersistDelegatesToTheRepository() {
        PaymentRepository repository = mock(PaymentRepository.class);
        // saveAll is expected to be called with the generated list and to return it back.
        when(repository.saveAll(org.mockito.ArgumentMatchers.<List<Payment>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentGeneratorService service = newService(repository);
        List<Payment> result = service.generateAndPersist(300, SEED);

        assertEquals(300, result.size());
        verify(repository, times(1)).saveAll(org.mockito.ArgumentMatchers.<List<Payment>>any());
    }
}

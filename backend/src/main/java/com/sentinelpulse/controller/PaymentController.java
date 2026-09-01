package com.sentinelpulse.controller;

import com.sentinelpulse.dto.PaymentDto;
import com.sentinelpulse.dto.PaymentGenerateRequest;
import com.sentinelpulse.dto.PaymentGenerateResponse;
import com.sentinelpulse.dto.PaymentSummaryResponse;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.service.PaymentGeneratorService;
import com.sentinelpulse.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST endpoints for the payment data engine: synthetic generation, filtered
 * listing, and the aggregate summary. Contains no business logic itself -
 * everything here delegates to {@link PaymentGeneratorService} or
 * {@link PaymentService}.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentGeneratorService paymentGeneratorService;
    private final PaymentService paymentService;

    public PaymentController(PaymentGeneratorService paymentGeneratorService, PaymentService paymentService) {
        this.paymentGeneratorService = paymentGeneratorService;
        this.paymentService = paymentService;
    }

    @PostMapping("/generate")
    public PaymentGenerateResponse generate(@Valid @RequestBody PaymentGenerateRequest request) {
        Instant before = Instant.now();
        List<Payment> generated = paymentGeneratorService.generateAndPersist(request.count(), request.seed());
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(paymentGeneratorService.getGenerationWindowHours(), ChronoUnit.HOURS);

        return new PaymentGenerateResponse(request.count(), generated.size(), windowStart, windowEnd, before);
    }

    @GetMapping
    public Page<PaymentDto> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String bank,
            @RequestParam(required = false) ErrorCode errorCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return paymentService.findPayments(status, paymentMethod, bank, errorCode, from, to, pageable);
    }

    @GetMapping("/summary")
    public PaymentSummaryResponse summary() {
        return paymentService.getSummary();
    }
}

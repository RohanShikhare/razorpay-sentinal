package com.sentinelpulse.service;

import com.sentinelpulse.dto.HealthResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Provides the current backend health status.
 *
 * Deliberately trivial in Module 0 - it exists so the controller has
 * nothing but request/response wiring, matching the project's rule that
 * business logic stays out of controllers even for simple endpoints.
 */
@Service
public class HealthService {

    public HealthResponse getHealth() {
        return new HealthResponse(
                "UP",
                "razorpay-sentinel",
                "module-0-foundation",
                Instant.now()
        );
    }
}

package com.sentinelpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Razorpay Sentinel - AI Payment Incident Commander.
 *
 * Entry point for the monolithic Spring Boot backend.
 *
 * Module 0 scope: application bootstrap, database wiring, REST foundation,
 * exception handling, validation, CORS, and a health endpoint only.
 * Payment, AI, policy, recovery, and audit functionality are intentionally
 * not implemented yet and will arrive in later modules.
 */
@SpringBootApplication
public class RazorpaySentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(RazorpaySentinelApplication.class, args);
    }
}

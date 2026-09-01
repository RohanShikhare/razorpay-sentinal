/**
 * JPA entities modeling payments, simulations, incidents, recovery
 * attempts, and audit records.
 *
 * {@link com.sentinelpulse.entity.Payment} was added in Module 1 (Payment
 * Data Engine). {@link com.sentinelpulse.entity.SimulationRun} and
 * {@link com.sentinelpulse.entity.SimulationChange} were added in Module 2
 * (Incident Simulator) — deliberately lightweight simulation records, not
 * detected incidents. {@link com.sentinelpulse.entity.Incident} was added
 * in Module 3 (Monitoring & Incident Detection Engine) - it records what
 * was statistically detected; {@code rootCause} stays null until a future
 * AI investigation module assigns one. Recovery and audit entities are
 * still planned for later modules.
 */
package com.sentinelpulse.entity;

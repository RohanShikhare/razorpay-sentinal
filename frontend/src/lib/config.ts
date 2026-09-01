/**
 * Central place for frontend runtime configuration.
 *
 * NEXT_PUBLIC_API_BASE_URL points at the Spring Boot backend. It defaults to
 * the local backend port so the app works out of the box during
 * development; override it via a .env.local file for other environments.
 */
export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") || "http://localhost:8080";

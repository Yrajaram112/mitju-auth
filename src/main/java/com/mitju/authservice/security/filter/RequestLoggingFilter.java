package com.mitju.authservice.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every inbound HTTP request and its response status + duration.
 * Runs at the very start of the filter chain (Order = HIGHEST_PRECEDENCE).
 *
 * Log format:
 *   --> POST /api/auth/login [ip=x.x.x.x]
 *   <-- POST /api/auth/login 200 OK [47ms]
 *
 * Sensitive paths (login, register) are logged without bodies.
 * The traceId is already in MDC from JwtAuthFilter; this filter adds
 * the request start timing before JwtAuthFilter runs.
 *
 * In production, consider replacing this with Micrometer's
 * ObservationFilter for richer metrics integration.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        long startNs = System.nanoTime();

        String method = request.getMethod();
        String uri    = request.getRequestURI();
        String query  = request.getQueryString();
        String fullUri = query != null ? uri + "?" + query : uri;
        String ip      = resolveClientIp(request);

        log.info("--> {} {} [ip={}]", method, fullUri, ip);

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            int  status     = response.getStatus();

            if (status >= 500) {
                log.error("<-- {} {} {} [{}ms]", method, fullUri, status, durationMs);
            } else if (status >= 400) {
                log.warn("<-- {} {} {} [{}ms]", method, fullUri, status, durationMs);
            } else {
                log.info("<-- {} {} {} [{}ms]", method, fullUri, status, durationMs);
            }
        }
    }

    /**
     * Resolves the real client IP, accounting for reverse proxies (Nginx, ALB).
     * Reads X-Forwarded-For header first; falls back to remoteAddr.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — take first (leftmost = client)
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** Skip logging for actuator endpoints to reduce noise in logs. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator");
    }
}

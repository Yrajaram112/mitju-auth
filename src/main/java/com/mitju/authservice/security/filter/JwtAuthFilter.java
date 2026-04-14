package com.mitju.authservice.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mitju.authservice.entity.User;
import com.mitju.authservice.security.JwtService;
import com.mitju.authservice.security.principal.UserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT authentication filter — runs once per request.
 *
 * Flow:
 *  1. Extract Bearer token from Authorization header.
 *  2. Validate and parse claims via JwtService (throws JwtAuthException on failure).
 *  3. Build UserPrincipal and set into SecurityContext.
 *  4. Stamp traceId into MDC for structured log correlation.
 *  5. On any auth failure write a clean JSON 401 — never let exceptions bubble
 *     to the default Spring error page which leaks stack traces.
 *
 * Bug fixed from original: auth context was set before validation, then set again
 * after an already-failed isTokenValid() check — the filter continued even on
 * invalid tokens.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX         = "Bearer ";
    private static final String MDC_TRACE_ID          = "traceId";
    private static final String MDC_USER_ID           = "userId";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Stamp a traceId into MDC so every log line for this request is correlated.
        // If the client sends X-Request-Id we honour it; otherwise generate one.
        String traceId = resolveTraceId(request);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            String token = extractBearerToken(request);

            if (token == null) {
                // No token — pass through. Downstream security rules decide if
                // the endpoint is public or protected.
                chain.doFilter(request, response);
                return;
            }

            // Step 1: validate and parse. Throws JwtAuthException on any failure.
            Claims claims;
            try {
                claims = jwtService.validateAccessToken(token);
            } catch (JwtService.JwtAuthException ex) {
                log.warn("[JwtFilter] Token validation failed | traceId={} path={} reason={}",
                        traceId, request.getRequestURI(), ex.getMessage());
                writeUnauthorizedResponse(response, ex.getMessage());
                return;  // stop filter chain — do not continue
            }

            // Step 2: build principal from validated claims
            UUID userId   = UUID.fromString(claims.getSubject());
            String email  = claims.get("email", String.class);
            String roleStr = claims.get("role", String.class);

            User.UserRole role;
            try {
                role = User.UserRole.valueOf(roleStr);
            } catch (IllegalArgumentException ex) {
                log.warn("[JwtFilter] Unknown role in token: '{}' | traceId={}", roleStr, traceId);
                writeUnauthorizedResponse(response, "Invalid role in token");
                return;
            }

            UserPrincipal principal = new UserPrincipal(userId, email, role);
            MDC.put(MDC_USER_ID, userId.toString());

            // Step 3: set auth in SecurityContext — exactly once, after validation
            var authToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
            );
            authToken.setDetails(request.getRemoteAddr());
            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.debug("[JwtFilter] Authenticated userId={} role={} path={}",
                    userId, role, request.getRequestURI());

            chain.doFilter(request, response);

        } finally {
            // Always clear MDC — threads are pooled, leftover context leaks into next request
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private String resolveTraceId(HttpServletRequest request) {
        String clientId = request.getHeader("X-Request-Id");
        return StringUtils.hasText(clientId) ? clientId : UUID.randomUUID().toString();
    }

    /** Writes a clean JSON 401 body — no stack trace, no HTML. */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", 401,
                "error",  "Unauthorized",
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}

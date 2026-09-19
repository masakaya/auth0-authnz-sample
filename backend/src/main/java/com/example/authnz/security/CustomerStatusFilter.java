package com.example.authnz.security;

import com.example.authnz.ledger.CustomerLedgerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects every request carrying a token whose subject maps to a non-{@code active} customer
 * in the ledger, not just requests to endpoints an authority check would otherwise catch.
 * Only registered when {@code app.auth.authority-source=ledger}; the claims-based
 * configuration has no equivalent check, since it trusts the identity provider to keep
 * blocked users from obtaining a token at all.
 *
 * <p>Added to the filter chain after {@code BearerTokenAuthenticationFilter} (see {@link
 * SecurityConfig}) so that {@link LedgerAuthorityResolver} has already resolved (and, for a
 * first-seen subject, provisioned) the customer row this filter reads.
 */
@Component
@ConditionalOnProperty(prefix = "app.auth", name = "authority-source", havingValue = "ledger")
public class CustomerStatusFilter extends OncePerRequestFilter {

    private static final String ACTIVE_STATUS = "active";

    private final CustomerLedgerRepository customerLedgerRepository;

    public CustomerStatusFilter(CustomerLedgerRepository customerLedgerRepository) {
        this.customerLedgerRepository = customerLedgerRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String subject = jwtAuthentication.getToken().getSubject();
            String status = customerLedgerRepository.findStatus(subject).orElse(ACTIVE_STATUS);
            if (!ACTIVE_STATUS.equals(status)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"customer_not_active\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}

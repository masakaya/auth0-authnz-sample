package com.example.authnz.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for validating access tokens issued by the identity provider and for
 * deriving Spring Security authorities from their claims.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String issuer,
        String audience,
        String claimNamespace,
        List<String> knownBrands,
        AuthorityResolutionSource authoritySource) {

    /**
     * Where {@link com.example.authnz.security.AuthorityResolver} implementations read
     * authority information from. Only {@link #CLAIMS} is implemented today; {@link #LEDGER}
     * is reserved for a follow-up change that resolves authorities against the customer
     * ledger instead of trusting token claims directly.
     */
    public enum AuthorityResolutionSource {
        CLAIMS,
        LEDGER
    }
}

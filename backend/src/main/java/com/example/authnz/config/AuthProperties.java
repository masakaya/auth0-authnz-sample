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
     * authority information from. {@link #CLAIMS} trusts the token's own claims; {@link
     * #LEDGER} instead looks up the customer ledger by the token's {@code sub} (see {@link
     * com.example.authnz.security.LedgerAuthorityResolver}), which also requires the ledger
     * database described in {@code compose.yaml} and the Flyway migrations.
     */
    public enum AuthorityResolutionSource {
        CLAIMS,
        LEDGER
    }
}

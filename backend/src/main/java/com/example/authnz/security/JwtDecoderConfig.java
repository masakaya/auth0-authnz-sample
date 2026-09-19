package com.example.authnz.security;

import com.example.authnz.config.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Wires the {@link JwtDecoder} used by the OAuth2 resource server filter chain: standard
 * timestamp checks, plus issuer and audience validation. The {@code azp} claim is
 * intentionally not checked.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(AuthProperties authProperties) {
        String issuer = normalizeIssuer(authProperties.issuer());
        // withJwkSetUri only records the URI; the key set is fetched lazily on first decode,
        // so building this bean never makes a network call at startup. withIssuerLocation is
        // deliberately avoided because it would fetch the discovery document eagerly.
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(issuer + ".well-known/jwks.json").build();
        decoder.setJwtValidator(buildValidator(issuer, authProperties.audience()));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> buildValidator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> withTimestamp = JwtValidators.createDefault();
        OAuth2TokenValidator<Jwt> withIssuer = new JwtIssuerValidator(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audience);
        return new DelegatingOAuth2TokenValidator<>(withTimestamp, withIssuer, withAudience);
    }

    static String normalizeIssuer(String issuer) {
        return issuer.endsWith("/") ? issuer : issuer + "/";
    }
}

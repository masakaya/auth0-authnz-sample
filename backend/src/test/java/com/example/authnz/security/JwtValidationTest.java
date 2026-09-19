package com.example.authnz.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies the issuer/audience/timestamp validator chain built by {@link JwtDecoderConfig}
 * against locally self-signed tokens, so no network access to a real identity provider is
 * required.
 */
class JwtValidationTest {

    private static final String ISSUER = "https://example.auth0.com/";
    private static final String AUDIENCE = "https://api.example.com";

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @BeforeEach
    void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();
    }

    @Test
    void acceptsValidToken() {
        Jwt jwt = decode(sign(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60)));

        assertThat(jwt.getSubject()).isEqualTo("user-1");
    }

    @Test
    void rejectsWrongIssuer() {
        String token = sign("https://wrong-tenant.auth0.com/", List.of(AUDIENCE), Instant.now().plusSeconds(60));

        assertThat(decodeFails(token)).isTrue();
    }

    @Test
    void rejectsWrongAudience() {
        String token = sign(ISSUER, List.of("https://other-api.example.com"), Instant.now().plusSeconds(60));

        assertThat(decodeFails(token)).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        String token = sign(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(60));

        assertThat(decodeFails(token)).isTrue();
    }

    @Test
    void acceptsArrayAudienceContainingRequiredValue() {
        Jwt jwt = decode(sign(ISSUER, List.of("https://other-api.example.com", AUDIENCE), Instant.now().plusSeconds(60)));

        assertThat(jwt.getAudience()).contains(AUDIENCE);
    }

    private Jwt decode(String token) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(JwtDecoderConfig.buildValidator(ISSUER, AUDIENCE));
        return decoder.decode(token);
    }

    private boolean decodeFails(String token) {
        try {
            decode(token);
            return false;
        } catch (JwtException expected) {
            return true;
        }
    }

    private String sign(String issuer, List<String> audience, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject("user-1")
                    .issueTime(Date.from(Instant.now().minusSeconds(10)))
                    .expirationTime(Date.from(expiresAt))
                    .build();
            SignedJWT signedJwt =
                    new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
            signedJwt.sign(new RSASSASigner(privateKey));
            return signedJwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test token", e);
        }
    }
}

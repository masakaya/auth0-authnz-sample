package com.example.authnz.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authnz.ledger.Customer;
import com.example.authnz.ledger.CustomerLedgerRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises {@link LedgerAuthorityResolver} and {@link CustomerStatusFilter} end to end
 * through the real security filter chain, against a real Postgres migrated with the same
 * Flyway scripts used in production (see {@code db/migration}). Runs against its own
 * Testcontainers Postgres rather than the developer's {@code docker compose} database, so
 * this test's outcome never depends on the state of that container.
 *
 * <p>Unlike {@link com.example.authnz.web.ApiControllerSecurityTest}, this test cannot use
 * the {@code jwt()} MockMvc request post-processor: that post-processor injects a fully
 * resolved {@link org.springframework.security.core.Authentication} straight into the
 * security context, bypassing both {@link LedgerAuthorityResolver} (invoked from the real
 * {@code JwtAuthenticationConverter} during authentication) and {@link
 * CustomerStatusFilter} (a filter later in the chain). Instead, every request here carries a
 * real {@code Authorization: Bearer} header, decoded by a stub {@link JwtDecoder} (see
 * {@link StubJwtDecoderConfig}) so no real identity provider or signature is needed.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.auth.authority-source=ledger")
@AutoConfigureMockMvc
class LedgerAuthorityResolverSecurityTest {

    private static final String NAMESPACE = "https://authnz.example.com";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private static final Map<String, Jwt> TOKENS = new ConcurrentHashMap<>();
    private static final AtomicInteger TOKEN_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerLedgerRepository customerLedgerRepository;

    @BeforeAll
    static void migrateLedgerSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void brandAOnlyRole1Customer_getsBrandAOffersOnly() throws Exception {
        String token = tokenFor("auth0|sample-brand-a-role1");

        mockMvc.perform(authorized(get("/api/brand-a/offers"), token)).andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/brand-b/offers"), token)).andExpect(status().isForbidden());
        mockMvc.perform(authorized(get("/api/brand-a/role2-only"), token)).andExpect(status().isForbidden());
    }

    @Test
    void bothBrandsRole2Customer_getsAllThreeBrandEndpoints() throws Exception {
        String token = tokenFor("auth0|sample-both-brands-role2");

        mockMvc.perform(authorized(get("/api/brand-a/offers"), token)).andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/brand-b/offers"), token)).andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/brand-a/role2-only"), token)).andExpect(status().isOk());
    }

    @Test
    void suspendedCustomer_isForbiddenFromEveryRequest() throws Exception {
        String token = tokenFor("auth0|sample-suspended-brand-a-role2");

        mockMvc.perform(authorized(get("/api/private"), token))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"customer_not_active\"}"));
        mockMvc.perform(authorized(get("/api/brand-a/offers"), token)).andExpect(status().isForbidden());
    }

    @Test
    void unknownSubject_isProvisionedActiveWithNoBrandsOrRole() throws Exception {
        String subject = "auth0|not-in-ledger-" + UUID.randomUUID();
        String token = tokenFor(subject);

        mockMvc.perform(authorized(get("/api/private"), token)).andExpect(status().isOk());
        mockMvc.perform(authorized(get("/api/brand-a/offers"), token)).andExpect(status().isForbidden());

        Customer provisioned = customerLedgerRepository.findBySubject(subject).orElseThrow();
        assertThat(provisioned.status()).isEqualTo("active");
        assertThat(provisioned.brandIds()).isEmpty();
        assertThat(provisioned.customerRole()).isNull();
    }

    @Test
    void ledgerIgnoresBrandClaimNotGrantedInLedger() throws Exception {
        // This customer's ledger row has brand-b only; the token claims brand-a as well, but
        // the ledger-mode resolver must not trust that claim.
        String token = tokenFor("auth0|sample-brand-b-role2", Map.of(NAMESPACE + "/brands", List.of("brand-a")));

        mockMvc.perform(authorized(get("/api/brand-a/offers"), token)).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, String token) {
        return request.header(AUTHORIZATION, "Bearer " + token);
    }

    private static String tokenFor(String subject) {
        return tokenFor(subject, Map.of());
    }

    private static String tokenFor(String subject, Map<String, Object> extraClaims) {
        String tokenValue = "test-token-" + TOKEN_SEQUENCE.incrementAndGet();
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        Jwt jwt = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        TOKENS.put(tokenValue, jwt);
        return tokenValue;
    }

    /**
     * Lets requests here carry a real {@code Authorization: Bearer} header (see class
     * javadoc) without needing a real identity provider: this decoder just looks the token
     * value up in {@link #TOKENS}, exactly as {@link JwtDecoderConfig}'s real bean would
     * decode and validate a real token.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubJwtDecoderConfig {

        @Bean
        @Primary
        JwtDecoder stubJwtDecoder() {
            return token -> {
                Jwt jwt = TOKENS.get(token);
                if (jwt == null) {
                    throw new JwtException("Unknown test token: " + token);
                }
                return jwt;
            };
        }
    }
}

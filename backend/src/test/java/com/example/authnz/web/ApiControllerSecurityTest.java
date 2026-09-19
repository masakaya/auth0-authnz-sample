package com.example.authnz.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authnz.security.AuthorityResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Exercises the real security filter chain and {@code @PreAuthorize} annotations through
 * MockMvc. {@code jwt()} bypasses the {@link org.springframework.security.oauth2.jwt.JwtDecoder}
 * (no network access needed), but the claims-to-authorities mapping is still exercised for
 * real by resolving authorities with the application's own {@link AuthorityResolver} bean
 * before handing them to the request post processor.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerSecurityTest {

    private static final String NAMESPACE = "https://authnz.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthorityResolver authorityResolver;

    @Test
    void publicEndpoint_withoutToken_isOk() throws Exception {
        mockMvc.perform(get("/api/public")).andExpect(status().isOk());
    }

    @Test
    void publicEndpoint_withInvalidBearerToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/public").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void privateEndpoint_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/private")).andExpect(status().isUnauthorized());
    }

    @Test
    void privateEndpoint_withToken_isOk() throws Exception {
        mockMvc.perform(get("/api/private").with(jwtWithClaims(Map.of()))).andExpect(status().isOk());
    }

    @Test
    void brandAOffers_withBrandAOnly_isOk() throws Exception {
        mockMvc.perform(get("/api/brand-a/offers")
                        .with(jwtWithClaims(Map.of(NAMESPACE + "/brands", List.of("brand-a")))))
                .andExpect(status().isOk());
    }

    @Test
    void brandAOffers_withBrandBOnly_isForbidden() throws Exception {
        mockMvc.perform(get("/api/brand-a/offers")
                        .with(jwtWithClaims(Map.of(NAMESPACE + "/brands", List.of("brand-b")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void brandAOffers_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/brand-a/offers")).andExpect(status().isUnauthorized());
    }

    @Test
    void brandAOffers_withoutBrandClaim_isForbidden() throws Exception {
        mockMvc.perform(get("/api/brand-a/offers").with(jwtWithClaims(Map.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void brandBOffers_withBothBrands_isOk() throws Exception {
        mockMvc.perform(get("/api/brand-b/offers")
                        .with(jwtWithClaims(Map.of(NAMESPACE + "/brands", List.of("brand-a", "brand-b")))))
                .andExpect(status().isOk());
    }

    @Test
    void brandARole2Only_withBrandAAndRole2_isOk() throws Exception {
        mockMvc.perform(get("/api/brand-a/role2-only")
                        .with(jwtWithClaims(Map.of(
                                NAMESPACE + "/brands", List.of("brand-a"),
                                NAMESPACE + "/customer_role", "role2"))))
                .andExpect(status().isOk());
    }

    @Test
    void brandARole2Only_withBrandAAndRole1_isForbidden() throws Exception {
        mockMvc.perform(get("/api/brand-a/role2-only")
                        .with(jwtWithClaims(Map.of(
                                NAMESPACE + "/brands", List.of("brand-a"),
                                NAMESPACE + "/customer_role", "role1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_withReadAdminPermission_isOk() throws Exception {
        mockMvc.perform(get("/api/admin").with(jwtWithClaims(Map.of("permissions", List.of("read:admin")))))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpoint_withoutPermission_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin").with(jwtWithClaims(Map.of()))).andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin")).andExpect(status().isUnauthorized());
    }

    private RequestPostProcessor jwtWithClaims(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return jwt().jwt(jwt).authorities(authorityResolver.resolve(jwt));
    }
}

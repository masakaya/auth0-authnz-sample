package com.example.authnz.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.authnz.config.AuthProperties;
import com.example.authnz.config.AuthProperties.AuthorityResolutionSource;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ClaimsAuthorityResolverTest {

    private static final String NAMESPACE = "https://authnz.example.com";

    private final AuthProperties authProperties = new AuthProperties(
            "https://example.auth0.com/",
            "https://api.example.com",
            NAMESPACE,
            List.of("brand-a", "brand-b"),
            AuthorityResolutionSource.CLAIMS);

    private final ClaimsAuthorityResolver resolver = new ClaimsAuthorityResolver(authProperties);

    @Test
    void mapsPermissionsClaimToAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("permissions", List.of("read:admin", "write:admin")));

        assertThat(authorityNames(resolver.resolve(jwt))).containsExactlyInAnyOrder("read:admin", "write:admin");
    }

    @Test
    void mapsKnownBrandToRole() {
        Jwt jwt = jwtWithClaims(Map.of(NAMESPACE + "/brands", List.of("brand-a")));

        assertThat(authorityNames(resolver.resolve(jwt))).containsExactly("ROLE_BRAND_A");
    }

    @Test
    void ignoresUnknownBrand() {
        Jwt jwt = jwtWithClaims(Map.of(NAMESPACE + "/brands", List.of("brand-z")));

        assertThat(resolver.resolve(jwt)).isEmpty();
    }

    @Test
    void mapsRole1CustomerRoleClaim() {
        Jwt jwt = jwtWithClaims(Map.of(NAMESPACE + "/customer_role", "role1"));

        assertThat(authorityNames(resolver.resolve(jwt))).containsExactly("ROLE_ROLE1");
    }

    @Test
    void mapsRole2CustomerRoleClaim() {
        Jwt jwt = jwtWithClaims(Map.of(NAMESPACE + "/customer_role", "role2"));

        assertThat(authorityNames(resolver.resolve(jwt))).containsExactly("ROLE_ROLE2");
    }

    @Test
    void ignoresUnknownCustomerRole() {
        Jwt jwt = jwtWithClaims(Map.of(NAMESPACE + "/customer_role", "vip"));

        assertThat(resolver.resolve(jwt)).isEmpty();
    }

    @Test
    void ignoresMissingClaims() {
        Jwt jwt = jwtWithClaims(Map.of());

        assertThat(resolver.resolve(jwt)).isEmpty();
    }

    @Test
    void ignoresWrongTypedClaims() {
        Jwt jwt = jwtWithClaims(Map.of(
                "permissions", "read:admin",
                NAMESPACE + "/brands", "brand-a",
                NAMESPACE + "/customer_role", List.of("role1")));

        assertThat(resolver.resolve(jwt)).isEmpty();
    }

    private static List<String> authorityNames(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Map<String, Object> allClaims = new HashMap<>(claims);
        allClaims.putIfAbsent("sub", "user-1");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(c -> c.putAll(allClaims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}

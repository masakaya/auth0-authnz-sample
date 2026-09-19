package com.example.authnz.security;

import com.example.authnz.config.AuthProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Derives authorities directly from access token claims:
 *
 * <ul>
 *   <li>{@code permissions} (string array) becomes a same-named authority, e.g. {@code read:admin}.
 *   <li>{@code <namespace>/brands} (string array) becomes {@code ROLE_BRAND_<ID>} for every
 *       value present in {@code app.auth.known-brands}; unknown values are ignored.
 *   <li>{@code <namespace>/customer_role} (string) becomes {@code ROLE_ROLE1} or {@code
 *       ROLE_ROLE2} for the values {@code role1} / {@code role2}; anything else is ignored.
 * </ul>
 *
 * A missing or wrongly typed claim never fails resolution; it simply contributes no
 * authority for that claim.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.auth",
        name = "authority-source",
        havingValue = "claims",
        matchIfMissing = true)
public class ClaimsAuthorityResolver implements AuthorityResolver {

    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String BRANDS_CLAIM_SUFFIX = "/brands";
    private static final String CUSTOMER_ROLE_CLAIM_SUFFIX = "/customer_role";
    private static final String ROLE1_CLAIM_VALUE = "role1";
    private static final String ROLE2_CLAIM_VALUE = "role2";

    private final AuthProperties authProperties;

    public ClaimsAuthorityResolver(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public Collection<GrantedAuthority> resolve(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addPermissionAuthorities(jwt, authorities);
        addBrandAuthorities(jwt, authorities);
        addCustomerRoleAuthority(jwt, authorities);
        return authorities;
    }

    private void addPermissionAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        for (String permission : readStringList(jwt, PERMISSIONS_CLAIM)) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
    }

    private void addBrandAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        List<String> knownBrands = authProperties.knownBrands() == null ? List.of() : authProperties.knownBrands();
        String brandsClaim = authProperties.claimNamespace() + BRANDS_CLAIM_SUFFIX;
        for (String brand : readStringList(jwt, brandsClaim)) {
            if (knownBrands.contains(brand)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + toRoleSuffix(brand)));
            }
        }
    }

    private void addCustomerRoleAuthority(Jwt jwt, Set<GrantedAuthority> authorities) {
        String customerRoleClaim = authProperties.claimNamespace() + CUSTOMER_ROLE_CLAIM_SUFFIX;
        Object value = jwt.getClaims().get(customerRoleClaim);
        if (value instanceof String customerRole) {
            switch (customerRole) {
                case ROLE1_CLAIM_VALUE -> authorities.add(new SimpleGrantedAuthority("ROLE_ROLE1"));
                case ROLE2_CLAIM_VALUE -> authorities.add(new SimpleGrantedAuthority("ROLE_ROLE2"));
                default -> {
                    // Unknown customer_role values are ignored (fail closed).
                }
            }
        }
    }

    private static List<String> readStringList(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof String stringItem) {
                result.add(stringItem);
            }
        }
        return result;
    }

    private static String toRoleSuffix(String brand) {
        return brand.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}

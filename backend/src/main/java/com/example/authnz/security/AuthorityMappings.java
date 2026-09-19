package com.example.authnz.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authority-naming rules shared by every {@link AuthorityResolver} implementation. Both
 * {@link ClaimsAuthorityResolver} (which trusts token claims) and {@link
 * LedgerAuthorityResolver} (which trusts the customer ledger for brand/role membership, but
 * still reads {@code permissions} from the token) must agree on what a given brand id,
 * customer role value, or permission claim maps to, so switching {@code
 * app.auth.authority-source} never changes the resulting authority names.
 */
public final class AuthorityMappings {

    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ROLE1_VALUE = "role1";
    private static final String ROLE2_VALUE = "role2";
    private static final Pattern BRAND_ID_PATTERN = Pattern.compile("brand-[a-z0-9]+(-[a-z0-9]+)*");

    private AuthorityMappings() {}

    /**
     * Adds a same-named authority for every {@code permissions} claim value present on the
     * token, e.g. {@code read:admin}. A permission must never be able to pose as a role
     * (brand, customer role, or guest), so values starting with {@code ROLE_} are ignored.
     */
    public static void addPermissionAuthorities(Jwt jwt, Set<GrantedAuthority> authorities) {
        for (String permission : readStringList(jwt, PERMISSIONS_CLAIM)) {
            if (!permission.startsWith(ROLE_PREFIX)) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
    }

    /**
     * True when {@code brand} both matches the expected id format ({@code
     * brand-[a-z0-9]+(-[a-z0-9]+)*}) and is present in {@code knownBrands}. The format check
     * guards against a misconfigured known-brands entry (e.g. {@code "role2"}) colliding with
     * another role's authority name.
     */
    public static boolean isKnownBrand(String brand, List<String> knownBrands) {
        return brand != null
                && knownBrands != null
                && knownBrands.contains(brand)
                && BRAND_ID_PATTERN.matcher(brand).matches();
    }

    /** Converts a validated brand id such as {@code brand-a} to {@code ROLE_BRAND_A}. */
    public static GrantedAuthority brandAuthority(String brand) {
        return new SimpleGrantedAuthority(ROLE_PREFIX + brand.toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    /** Converts a customer-role value ({@code role1} / {@code role2}) to its authority, if valid. */
    public static Optional<GrantedAuthority> customerRoleAuthority(String customerRole) {
        if (customerRole == null) {
            return Optional.empty();
        }
        return switch (customerRole) {
            case ROLE1_VALUE -> Optional.of(new SimpleGrantedAuthority("ROLE_ROLE1"));
            case ROLE2_VALUE -> Optional.of(new SimpleGrantedAuthority("ROLE_ROLE2"));
            default -> Optional.empty();
        };
    }

    /** Reads a claim expected to be a JSON string array, ignoring non-string entries. */
    public static List<String> readStringList(Jwt jwt, String claimName) {
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
}

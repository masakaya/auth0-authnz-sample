package com.example.authnz.security;

import com.example.authnz.config.AuthProperties;
import com.example.authnz.ledger.Customer;
import com.example.authnz.ledger.CustomerLedgerRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Derives authorities from the customer ledger instead of trusting token claims: the
 * token's {@code sub} looks up (or provisions) a {@code customer} row, and brand / customer
 * role authorities are granted only while that row's status is {@code active}. The {@code
 * permissions} claim is still read from the token itself, exactly as {@link
 * ClaimsAuthorityResolver} does, via the shared {@link AuthorityMappings} rules.
 *
 * <p>A non-active customer is not merely denied brand/role authorities here: {@link
 * CustomerStatusFilter} rejects every request from such a subject outright. Granting no
 * authority for them here as well means method security also fails closed if that filter is
 * ever removed or bypassed.
 */
@Component
@ConditionalOnProperty(prefix = "app.auth", name = "authority-source", havingValue = "ledger")
public class LedgerAuthorityResolver implements AuthorityResolver {

    private static final String ACTIVE_STATUS = "active";

    private final CustomerLedgerRepository customerLedgerRepository;
    private final AuthProperties authProperties;

    public LedgerAuthorityResolver(CustomerLedgerRepository customerLedgerRepository, AuthProperties authProperties) {
        this.customerLedgerRepository = customerLedgerRepository;
        this.authProperties = authProperties;
    }

    @Override
    public Collection<GrantedAuthority> resolve(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        String subject = jwt.getSubject();
        if (subject == null) {
            return authorities;
        }

        Customer customer = customerLedgerRepository.findOrProvision(subject);
        if (!ACTIVE_STATUS.equals(customer.status())) {
            return authorities;
        }

        AuthorityMappings.addPermissionAuthorities(jwt, authorities);
        addBrandAuthorities(customer, authorities);
        AuthorityMappings.customerRoleAuthority(customer.customerRole()).ifPresent(authorities::add);
        return authorities;
    }

    private void addBrandAuthorities(Customer customer, Set<GrantedAuthority> authorities) {
        List<String> knownBrands = authProperties.knownBrands() == null ? List.of() : authProperties.knownBrands();
        for (String brandId : customer.brandIds()) {
            if (AuthorityMappings.isKnownBrand(brandId, knownBrands)) {
                authorities.add(AuthorityMappings.brandAuthority(brandId));
            }
        }
    }
}

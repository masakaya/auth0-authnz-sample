package com.example.authnz.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Derives the {@link GrantedAuthority} set for an authenticated request from a validated
 * access token. Implementations must fail closed: a missing, malformed, or unknown claim
 * value must simply omit the corresponding authority rather than throw.
 */
public interface AuthorityResolver {

    Collection<GrantedAuthority> resolve(Jwt jwt);
}

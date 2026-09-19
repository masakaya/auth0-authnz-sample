package com.example.authnz.web;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample endpoints exercised by the resource-server test suite. The security filter chain
 * permits every request; all access control happens here via {@code @PreAuthorize} so that
 * an anonymous caller without a valid token is rejected with 401 (no authentication),
 * while an authenticated caller lacking the required authority gets 403.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/public")
    @PreAuthorize("hasAnyRole('GUEST','ROLE1','ROLE2')")
    public ApiResponse publicEndpoint() {
        return toResponse("/api/public");
    }

    @GetMapping("/private")
    @PreAuthorize("isAuthenticated() and !isAnonymous()")
    public ApiResponse privateEndpoint() {
        return toResponse("/api/private");
    }

    @GetMapping("/brand-a/offers")
    @PreAuthorize("hasRole('BRAND_A')")
    public ApiResponse brandAOffers() {
        return toResponse("/api/brand-a/offers");
    }

    @GetMapping("/brand-b/offers")
    @PreAuthorize("hasRole('BRAND_B')")
    public ApiResponse brandBOffers() {
        return toResponse("/api/brand-b/offers");
    }

    @GetMapping("/brand-a/role2-only")
    @PreAuthorize("hasRole('BRAND_A') and hasRole('ROLE2')")
    public ApiResponse brandARole2Only() {
        return toResponse("/api/brand-a/role2-only");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('read:admin')")
    public ApiResponse admin() {
        return toResponse("/api/admin");
    }

    /**
     * Reads the current {@link Authentication} straight from the {@link SecurityContextHolder}
     * rather than taking it as a controller method parameter: Spring MVC's principal argument
     * resolver falls back to {@code HttpServletRequest#getUserPrincipal()}, which Spring
     * Security intentionally returns as {@code null} for anonymous authentication, causing a
     * {@code NullPointerException} on the {@code /api/public} anonymous path.
     */
    private ApiResponse toResponse(String endpoint) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<String> authorities =
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        return new ApiResponse(endpoint, authentication.getName(), authorities);
    }
}

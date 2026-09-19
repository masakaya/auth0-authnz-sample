package com.example.authnz.ledger;

import java.util.List;

/**
 * A row of the {@code customer} table, joined with the brand ids it has in {@code
 * customer_brand}. This is the authoritative record {@link
 * com.example.authnz.security.LedgerAuthorityResolver} and {@link
 * com.example.authnz.security.CustomerStatusFilter} use instead of trusting token claims.
 *
 * @param status one of {@code active}, {@code suspended}, {@code withdrawn}
 * @param customerRole {@code role1}, {@code role2}, or {@code null} when no role is set
 */
public record Customer(long id, String subject, String status, String customerRole, List<String> brandIds) {}

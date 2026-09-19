package com.example.authnz.ledger;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for the {@code customer} / {@code customer_brand} tables created by the Flyway
 * migrations under {@code db/migration}. Only instantiated when {@code
 * app.auth.authority-source=ledger}, since {@link
 * com.example.authnz.security.LedgerAuthorityResolver} and {@link
 * com.example.authnz.security.CustomerStatusFilter} are its only callers.
 */
@Repository
public class CustomerLedgerRepository {

    static final String ACTIVE_STATUS = "active";

    private final JdbcClient jdbcClient;

    public CustomerLedgerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Customer> findBySubject(String subject) {
        Optional<CustomerRow> row = jdbcClient
                .sql("select id, status, customer_role from customer where subject = :subject")
                .param("subject", subject)
                .query((rs, rowNum) ->
                        new CustomerRow(rs.getLong("id"), rs.getString("status"), rs.getString("customer_role")))
                .optional();
        return row.map(r -> new Customer(r.id(), subject, r.status(), r.customerRole(), findBrandIds(r.id())));
    }

    /** Reads only the customer's status, for {@link com.example.authnz.security.CustomerStatusFilter}. */
    public Optional<String> findStatus(String subject) {
        return jdbcClient
                .sql("select status from customer where subject = :subject")
                .param("subject", subject)
                .query(String.class)
                .optional();
    }

    /**
     * Returns the existing customer for {@code subject}, or provisions a new {@value
     * #ACTIVE_STATUS} customer with no brands or role on first access (e.g. a customer known
     * to the identity provider but not yet reflected in the legacy ledger). The insert uses
     * {@code ON CONFLICT DO NOTHING} so two concurrent first requests for the same subject
     * never race into a unique-constraint violation.
     */
    public Customer findOrProvision(String subject) {
        return findBySubject(subject).orElseGet(() -> {
            jdbcClient
                    .sql("insert into customer (subject, status) values (:subject, :status) "
                            + "on conflict (subject) do nothing")
                    .param("subject", subject)
                    .param("status", ACTIVE_STATUS)
                    .update();
            return findBySubject(subject)
                    .orElseThrow(() -> new IllegalStateException("customer not found after provisioning: " + subject));
        });
    }

    private List<String> findBrandIds(long customerId) {
        return jdbcClient
                .sql("select brand_id from customer_brand where customer_id = :customerId")
                .param("customerId", customerId)
                .query(String.class)
                .list();
    }

    private record CustomerRow(long id, String status, String customerRole) {}
}

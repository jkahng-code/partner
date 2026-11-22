package com.partner.config;

import com.partner.data.InMemoryLoanDatabase;
import com.partner.data.LoanDatabase;
import com.partner.domain.DelinquencyPolicy;
import com.partner.domain.Loan;
import com.partner.domain.LoanPayment;
import com.partner.web.DelinquencyController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the delinquency-detection slice (built as plain Java classes) into Spring so the
 * REST layer can use it. Seed data is in memory; every loan starts with a $10,000.00 principal.
 *
 * D-100  $500 missed, due 2026-08-01            late, but under both thresholds  -> NON_DELINQUENT
 * D-200  $2,600 missed, due 2026-10-01          more than 25% of principal       -> DELINQUENT
 * D-300  $50 missed, due 2026-04-15             six-month rule (from 2026-10-15) -> DELINQUENT
 * D-400  $400 paid in full before 2026-09-01    nothing missed                   -> NON_DELINQUENT
 * D-500  $2,500 missed, due 2026-10-01          exactly 25% (rule is "more than") -> NON_DELINQUENT
 */
@Configuration
public class DelinquencyConfig {

    private static final BigDecimal PRINCIPAL = new BigDecimal("10000.00");

    @Bean
    public DelinquencyPolicy delinquencyPolicy() {
        return new DelinquencyPolicy();
    }

    @Bean
    public LoanDatabase delinquencyLoanDatabase() {
        InMemoryLoanDatabase database = new InMemoryLoanDatabase();
        database.save(loan("D-100", missed("D-100-p1", "500.00", LocalDate.of(2026, 8, 1))));
        database.save(loan("D-200", missed("D-200-p1", "2600.00", LocalDate.of(2026, 10, 1))));
        database.save(loan("D-300", missed("D-300-p1", "50.00", LocalDate.of(2026, 4, 15))));
        database.save(loan("D-400", new LoanPayment("D-400-p1", new BigDecimal("400.00"),
                new BigDecimal("400.00"), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 28))));
        database.save(loan("D-500", missed("D-500-p1", "2500.00", LocalDate.of(2026, 10, 1))));
        return database;
    }

    @Bean
    public DelinquencyController delinquencyController(
            LoanDatabase delinquencyLoanDatabase, DelinquencyPolicy delinquencyPolicy) {
        return new DelinquencyController(delinquencyLoanDatabase, delinquencyPolicy);
    }

    private static Loan loan(String loanId, LoanPayment payment) {
        return new Loan(loanId, PRINCIPAL, List.of(payment));
    }

    private static LoanPayment missed(String paymentId, String amountDue, LocalDate dueDate) {
        return new LoanPayment(paymentId, new BigDecimal(amountDue), BigDecimal.ZERO, dueDate, null);
    }
}

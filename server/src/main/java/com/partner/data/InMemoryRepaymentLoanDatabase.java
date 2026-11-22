package com.partner.data;

import com.partner.repayment.Borrower;
import com.partner.repayment.Loan;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * In-memory store for repayment loans, seeded with sample data for demos.
 * Data resets every time the server restarts (no real database yet; see CP2 known gaps).
 */
@Component
public class InMemoryRepaymentLoanDatabase implements RepaymentLoanDatabase {
    private final Map<String, Loan> loans = new LinkedHashMap<>();

    public InMemoryRepaymentLoanDatabase() {
        save(new Loan("L-100", new Borrower("B-100", "Ava Martinez", true), new BigDecimal("1000.00")));
        save(new Loan("L-200", new Borrower("B-200", "Ben Carter", false), new BigDecimal("1200.00")));
        save(new Loan("L-300", new Borrower("B-300", "Chloe Nguyen", true), new BigDecimal("15000.00")));
    }

    @Override
    public Optional<Loan> findById(String loanId) {
        return Optional.ofNullable(loans.get(loanId));
    }

    @Override
    public List<Loan> findAll() {
        return new ArrayList<>(loans.values());
    }

    @Override
    public void save(Loan loan) {
        loans.put(loan.getLoanId(), loan);
    }
}

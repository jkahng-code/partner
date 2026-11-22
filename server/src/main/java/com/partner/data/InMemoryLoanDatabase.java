package com.partner.data;

import com.partner.domain.Loan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryLoanDatabase implements LoanDatabase {

    // LinkedHashMap keeps insertion order so GET /api/delinquency/loans lists loans predictably.
    private final Map<String, Loan> loans = new LinkedHashMap<>();

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

package com.partner.data;

import com.partner.domain.Loan;
import java.util.List;
import java.util.Optional;

public interface LoanDatabase {

    Optional<Loan> findById(String loanId);

    /** All stored loans, in insertion order. Added in CP2 so the delinquency API can list loans. */
    List<Loan> findAll();

    void save(Loan loan);
}

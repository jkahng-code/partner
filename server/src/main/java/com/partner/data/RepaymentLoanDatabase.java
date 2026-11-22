package com.partner.data;

import com.partner.repayment.Loan;
import java.util.List;
import java.util.Optional;

/** Data-layer access to loans for the repayment slice. */
public interface RepaymentLoanDatabase {
    Optional<Loan> findById(String loanId);

    List<Loan> findAll();

    void save(Loan loan);
}

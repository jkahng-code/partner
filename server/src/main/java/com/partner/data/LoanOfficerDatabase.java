package com.partner.data;

import com.partner.repayment.LoanOfficer;
import java.util.List;
import java.util.Optional;

/** Data-layer access to loan officers. */
public interface LoanOfficerDatabase {
    Optional<LoanOfficer> findById(String officerId);

    List<LoanOfficer> findAll();

    void save(LoanOfficer officer);
}

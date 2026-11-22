package com.partner.data;

import com.partner.repayment.LoanOfficer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** In-memory store for loan officers, seeded with two officers. Resets on server restart. */
@Component
public class InMemoryLoanOfficerDatabase implements LoanOfficerDatabase {
    private final Map<String, LoanOfficer> officers = new LinkedHashMap<>();

    public InMemoryLoanOfficerDatabase() {
        save(new LoanOfficer("O-1", "Dana Lee"));
        save(new LoanOfficer("O-2", "Marcus Reed"));
    }

    @Override
    public Optional<LoanOfficer> findById(String officerId) {
        return Optional.ofNullable(officers.get(officerId));
    }

    @Override
    public List<LoanOfficer> findAll() {
        return new ArrayList<>(officers.values());
    }

    @Override
    public void save(LoanOfficer officer) {
        officers.put(officer.getOfficerId(), officer);
    }
}

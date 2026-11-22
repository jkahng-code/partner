package com.partner.repayment;

/**
 * Minimal borrower for the repayment and officer-assignment slices. Delinquency detection belongs to
 * the delinquency slice; here it is just a flag the incentive and assignment rules read.
 */
public class Borrower {
    private final String borrowerId;
    private final String name;
    private final boolean delinquent;
    private LoanOfficer assignedOfficer;

    public Borrower(String borrowerId, String name, boolean delinquent) {
        this.borrowerId = borrowerId;
        this.name = name;
        this.delinquent = delinquent;
    }

    public String getBorrowerId() { return borrowerId; }
    public String getName() { return name; }
    public boolean isDelinquent() { return delinquent; }

    /** The officer this borrower is assigned to, or null if unassigned (at most one: 0..1). */
    public LoanOfficer getAssignedOfficer() { return assignedOfficer; }

    /** Package-private: only LoanOfficer.assignBorrower/unassignBorrower may change it. */
    void setAssignedOfficer(LoanOfficer officer) { this.assignedOfficer = officer; }
}

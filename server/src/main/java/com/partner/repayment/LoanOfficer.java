package com.partner.repayment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A loan officer who manages a caseload of delinquent borrowers.
 * Enforces: a borrower has at most one officer (0..1), only delinquent borrowers are assigned,
 * a caseload holds at most MAX_CASELOAD borrowers, and an officer can view only their own borrowers.
 */
public class LoanOfficer {
    public static final int MAX_CASELOAD = 25;

    private final String officerId;
    private final String name;
    private final List<Borrower> assignedBorrowers = new ArrayList<>();

    public LoanOfficer(String officerId, String name) {
        this.officerId = Objects.requireNonNull(officerId);
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Assigns a borrower to this officer's caseload.
     *
     * @throws IllegalStateException if the borrower is not delinquent, is already assigned to this or
     *                               another officer, or this officer's caseload is full
     */
    public void assignBorrower(Borrower borrower) {
        Objects.requireNonNull(borrower);
        if (!borrower.isDelinquent()) {
            throw new IllegalStateException("Only delinquent borrowers can be assigned to an officer");
        }
        if (borrower.getAssignedOfficer() == this) {
            throw new IllegalStateException("Borrower is already assigned to this officer");
        }
        if (borrower.getAssignedOfficer() != null) {
            throw new IllegalStateException("Borrower is already assigned to another officer");
        }
        if (assignedBorrowers.size() >= MAX_CASELOAD) {
            throw new IllegalStateException("Caseload is full (" + MAX_CASELOAD + " borrowers)");
        }
        assignedBorrowers.add(borrower);
        borrower.setAssignedOfficer(this);
    }

    /**
     * Removes a borrower from this officer's caseload.
     *
     * @throws IllegalStateException if the borrower is not assigned to this officer
     */
    public void unassignBorrower(Borrower borrower) {
        Objects.requireNonNull(borrower);
        if (borrower.getAssignedOfficer() != this) {
            throw new IllegalStateException("Borrower is not assigned to this officer");
        }
        assignedBorrowers.remove(borrower);
        borrower.setAssignedOfficer(null);
    }

    /** Access control: an officer may view only borrowers assigned to them. */
    public boolean canView(Borrower borrower) {
        return borrower != null && borrower.getAssignedOfficer() == this;
    }

    public String getOfficerId() { return officerId; }
    public String getName() { return name; }
    public int getCaseloadSize() { return assignedBorrowers.size(); }
    public List<Borrower> getAssignedBorrowers() { return Collections.unmodifiableList(assignedBorrowers); }
}

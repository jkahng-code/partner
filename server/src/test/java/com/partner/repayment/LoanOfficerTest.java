package com.partner.repayment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Officer-assignment tests. Basis: R6 (assign a delinquent borrower to an officer),
 * R7 (an officer can view only their own borrowers), I5 (a borrower has at most one officer),
 * I6 (a caseload holds at most 25 borrowers).
 * The tests also cover every branch of LoanOfficer.assignBorrower (4 decisions, 5 paths).
 */
class LoanOfficerTest {

    private LoanOfficer dana;
    private LoanOfficer marcus;

    @BeforeEach
    void setUp() {
        dana = new LoanOfficer("O-1", "Dana Lee");
        marcus = new LoanOfficer("O-2", "Marcus Reed");
    }

    private static Borrower delinquent(String id) {
        return new Borrower(id, "Borrower " + id, true);
    }

    private void fillCaseload(LoanOfficer officer, int count) {
        for (int i = 0; i < count; i++) {
            officer.assignBorrower(delinquent("B-fill-" + i));
        }
    }

    @Test // OA-1 (R6, R7) | assignBorrower path 5: all checks pass
    void assign_delinquentUnassignedBorrower_succeeds() {
        Borrower ava = delinquent("B-100");
        dana.assignBorrower(ava);
        assertSame(dana, ava.getAssignedOfficer());
        assertEquals(1, dana.getCaseloadSize());
        assertTrue(dana.canView(ava));
    }

    @Test // OA-2 (R6) | path 1: borrower not delinquent
    void assign_nonDelinquentBorrower_rejected() {
        Borrower ben = new Borrower("B-200", "Ben Carter", false);
        assertThrows(IllegalStateException.class, () -> dana.assignBorrower(ben));
        assertNull(ben.getAssignedOfficer());
        assertEquals(0, dana.getCaseloadSize());
    }

    @Test // OA-3 (I5) | path 2: already assigned to this officer
    void assign_sameBorrowerTwice_rejectedAndCountedOnce() {
        Borrower ava = delinquent("B-100");
        dana.assignBorrower(ava);
        assertThrows(IllegalStateException.class, () -> dana.assignBorrower(ava));
        assertEquals(1, dana.getCaseloadSize());
    }

    @Test // OA-4 (I5) | path 3: already assigned to another officer
    void assign_borrowerOwnedByAnotherOfficer_rejected() {
        Borrower ava = delinquent("B-100");
        dana.assignBorrower(ava);
        assertThrows(IllegalStateException.class, () -> marcus.assignBorrower(ava));
        assertSame(dana, ava.getAssignedOfficer());
        assertEquals(0, marcus.getCaseloadSize());
    }

    @Test // OA-5 (I6) boundary below: caseload 23 -> 24th accepted
    void assign_caseload23_accepts24th() {
        fillCaseload(dana, 23);
        dana.assignBorrower(delinquent("B-24th"));
        assertEquals(24, dana.getCaseloadSize());
    }

    @Test // OA-6 (I6) boundary at: caseload 24 -> 25th accepted, reaching the limit
    void assign_caseload24_accepts25th() {
        fillCaseload(dana, 24);
        dana.assignBorrower(delinquent("B-25th"));
        assertEquals(25, dana.getCaseloadSize());
    }

    @Test // OA-7 (I6) boundary above | path 4: caseload 25 -> 26th rejected
    void assign_caseloadFull_rejects26th() {
        fillCaseload(dana, 25);
        Borrower extra = delinquent("B-26th");
        assertThrows(IllegalStateException.class, () -> dana.assignBorrower(extra));
        assertEquals(25, dana.getCaseloadSize());
        assertNull(extra.getAssignedOfficer());
    }

    @Test // OA-8 (R7): an officer cannot view another officer's borrower
    void canView_anotherOfficersBorrower_false() {
        Borrower ava = delinquent("B-100");
        dana.assignBorrower(ava);
        assertFalse(marcus.canView(ava));
    }

    @Test // OA-9 (R7): no officer can view an unassigned borrower
    void canView_unassignedBorrower_false() {
        assertFalse(dana.canView(delinquent("B-100")));
    }

    @Test // OA-10 (R6, I6): unassigning frees the slot and removes access
    void unassign_freesSlotAndRemovesAccess() {
        fillCaseload(dana, 24);
        Borrower ava = delinquent("B-100");
        dana.assignBorrower(ava);           // caseload now 25 (full)
        dana.unassignBorrower(ava);
        assertEquals(24, dana.getCaseloadSize());
        assertNull(ava.getAssignedOfficer());
        assertFalse(dana.canView(ava));
        dana.assignBorrower(delinquent("B-new")); // the freed slot can be reused
        assertEquals(25, dana.getCaseloadSize());
    }

    @Test // OA-11 (I5): an officer cannot unassign someone else's borrower
    void unassign_notOwnBorrower_rejected() {
        Borrower ava = delinquent("B-100");
        dana.assignBorrower(ava);
        assertThrows(IllegalStateException.class, () -> marcus.unassignBorrower(ava));
        assertSame(dana, ava.getAssignedOfficer());
    }
}

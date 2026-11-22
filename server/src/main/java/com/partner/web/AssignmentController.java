package com.partner.web;

import com.partner.data.LoanOfficerDatabase;
import com.partner.data.RepaymentLoanDatabase;
import com.partner.repayment.Borrower;
import com.partner.repayment.Loan;
import com.partner.repayment.LoanOfficer;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer for the officer-assignment feature: assign/unassign borrowers to an officer's
 * caseload, and let an officer view a borrower only if that borrower is assigned to them.
 */
@RestController
@RequestMapping("/api/officers")
public class AssignmentController {

    private final LoanOfficerDatabase officers;
    private final RepaymentLoanDatabase loans;

    public AssignmentController(LoanOfficerDatabase officers, RepaymentLoanDatabase loans) {
        this.officers = officers;
        this.loans = loans;
    }

    // ---------- request / response shapes ----------
    public record AssignRequest(String borrowerId) {}

    public record OfficerView(String officerId, String name, int caseloadSize, int maxCaseload, List<String> borrowerIds) {
        static OfficerView of(LoanOfficer officer) {
            return new OfficerView(
                    officer.getOfficerId(),
                    officer.getName(),
                    officer.getCaseloadSize(),
                    LoanOfficer.MAX_CASELOAD,
                    officer.getAssignedBorrowers().stream().map(Borrower::getBorrowerId).toList());
        }
    }

    public record BorrowerCaseView(
            String borrowerId, String name, boolean delinquent, String assignedOfficerId,
            List<RepaymentController.LoanView> loans) {}

    // ---------- endpoints ----------

    /** List officers with their caseloads. */
    @GetMapping
    public List<OfficerView> listOfficers() {
        return officers.findAll().stream().map(OfficerView::of).toList();
    }

    /** Assign a borrower to an officer. Body: {"borrowerId": "B-100"} */
    @PostMapping("/{officerId}/borrowers")
    public ResponseEntity<OfficerView> assignBorrower(@PathVariable String officerId, @RequestBody AssignRequest request) {
        LoanOfficer officer = findOfficer(officerId);
        officer.assignBorrower(findBorrower(request.borrowerId()));
        officers.save(officer);
        return ResponseEntity.status(HttpStatus.CREATED).body(OfficerView.of(officer));
    }

    /** Remove a borrower from an officer's caseload. */
    @DeleteMapping("/{officerId}/borrowers/{borrowerId}")
    public OfficerView unassignBorrower(@PathVariable String officerId, @PathVariable String borrowerId) {
        LoanOfficer officer = findOfficer(officerId);
        officer.unassignBorrower(findBorrower(borrowerId));
        officers.save(officer);
        return OfficerView.of(officer);
    }

    /** An officer views one borrower's case. Refused with 403 unless the borrower is assigned to them. */
    @GetMapping("/{officerId}/borrowers/{borrowerId}")
    public BorrowerCaseView viewBorrower(@PathVariable String officerId, @PathVariable String borrowerId) {
        LoanOfficer officer = findOfficer(officerId);
        Borrower borrower = findBorrower(borrowerId);
        if (!officer.canView(borrower)) {
            throw new NotAssignedException(officerId, borrowerId);
        }
        List<RepaymentController.LoanView> borrowerLoans = loans.findAll().stream()
                .filter(loan -> loan.getBorrower() == borrower)
                .map(RepaymentController.LoanView::of)
                .toList();
        return new BorrowerCaseView(
                borrower.getBorrowerId(), borrower.getName(), borrower.isDelinquent(), officerId, borrowerLoans);
    }

    // ---------- helpers ----------

    private LoanOfficer findOfficer(String officerId) {
        return officers.findById(officerId).orElseThrow(() -> new NotFoundException("Unknown officer: " + officerId));
    }

    private Borrower findBorrower(String borrowerId) {
        return loans.findAll().stream()
                .map(Loan::getBorrower)
                .filter(b -> b.getBorrowerId().equals(borrowerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Unknown borrower: " + borrowerId));
    }

    // ---------- errors -> HTTP status codes ----------

    static class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }

    static class NotAssignedException extends RuntimeException {
        NotAssignedException(String officerId, String borrowerId) {
            super("Officer " + officerId + " is not assigned to borrower " + borrowerId);
        }
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NotAssignedException.class)
    public ResponseEntity<Map<String, String>> handleNotAssigned(NotAssignedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleRuleViolation(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}

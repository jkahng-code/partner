package com.partner.web;

import com.partner.data.RepaymentLoanDatabase;
import com.partner.repayment.IncentiveOffer;
import com.partner.repayment.Loan;
import com.partner.repayment.OfferStatus;
import com.partner.repayment.PlanStatus;
import com.partner.repayment.RegulatoryValidator;
import com.partner.repayment.RepaymentPlan;
import com.partner.repayment.StandardRegulatoryValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Presentation layer for the repayment plan and incentive offer features.
 * Each endpoint loads a loan, calls the existing domain method, and returns the result as JSON.
 * Business rules live in com.partner.repayment, not here.
 */
@RestController
@RequestMapping("/api/repayment")
public class RepaymentController {

    private final RepaymentLoanDatabase loans;
    private final RegulatoryValidator validator = new StandardRegulatoryValidator();

    public RepaymentController(RepaymentLoanDatabase loans) {
        this.loans = loans;
    }

    // ---------- request bodies ----------
    public record CreatePlanRequest(int termMonths) {}

    public record DateRequest(LocalDate date) {}

    // ---------- response shapes ----------
    public record OfferView(
            BigDecimal reducedPaymentAmount,
            int reducedMonths,
            BigDecimal catchUpPaymentAmount,
            int catchUpMonths,
            BigDecimal totalScheduled,
            LocalDate expirationDate,
            OfferStatus status) {

        static OfferView of(IncentiveOffer offer) {
            if (offer == null) {
                return null;
            }
            int termMonths = offer.getRepaymentPlan().getTermMonths();
            return new OfferView(
                    offer.getReducedPaymentAmount(),
                    IncentiveOffer.REDUCED_MONTHS,
                    offer.getCatchUpPaymentAmount(),
                    termMonths - IncentiveOffer.REDUCED_MONTHS,
                    offer.getTotalScheduled(),
                    offer.getExpirationDate(),
                    offer.getStatus());
        }
    }

    public record PlanView(int termMonths, BigDecimal monthlyAmount, PlanStatus status, OfferView incentiveOffer) {
        static PlanView of(RepaymentPlan plan) {
            if (plan == null) {
                return null;
            }
            return new PlanView(
                    plan.getTermMonths(),
                    plan.getNewMonthlyAmount(),
                    plan.getStatus(),
                    OfferView.of(plan.getIncentiveOffer()));
        }
    }

    public record LoanView(
            String loanId, String borrowerName, boolean borrowerDelinquent, BigDecimal balance, PlanView activePlan) {
        static LoanView of(Loan loan) {
            return new LoanView(
                    loan.getLoanId(),
                    loan.getBorrower().getName(),
                    loan.getBorrower().isDelinquent(),
                    loan.getBalance(),
                    PlanView.of(loan.getActivePlan()));
        }
    }

    // ---------- endpoints ----------

    /** List every loan with its current plan, if any. */
    @GetMapping("/loans")
    public List<LoanView> listLoans() {
        return loans.findAll().stream().map(LoanView::of).toList();
    }

    /** One loan with its current plan and offer. */
    @GetMapping("/loans/{loanId}")
    public LoanView getLoan(@PathVariable String loanId) {
        return LoanView.of(findLoan(loanId));
    }

    /** R1: create a repayment plan. Body: {"termMonths": 10} */
    @PostMapping("/loans/{loanId}/plan")
    public ResponseEntity<PlanView> createPlan(@PathVariable String loanId, @RequestBody CreatePlanRequest request) {
        Loan loan = findLoan(loanId);
        RepaymentPlan plan = loan.createRepaymentPlan(request.termMonths());
        loans.save(loan);
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanView.of(plan));
    }

    /** R2: generate an incentive offer. Optional body: {"date": "2026-11-01"} (defaults to today). */
    @PostMapping("/loans/{loanId}/plan/offer")
    public ResponseEntity<OfferView> generateOffer(
            @PathVariable String loanId, @RequestBody(required = false) DateRequest request) {
        Loan loan = findLoan(loanId);
        IncentiveOffer offer = findPlan(loan).generateIncentiveOffer(dateOrToday(request));
        loans.save(loan);
        return ResponseEntity.status(HttpStatus.CREATED).body(OfferView.of(offer));
    }

    /** R3: borrower accepts the offer. Optional body: {"date": "2026-11-15"} (defaults to today). */
    @PostMapping("/loans/{loanId}/plan/offer/accept")
    public OfferView acceptOffer(@PathVariable String loanId, @RequestBody(required = false) DateRequest request) {
        Loan loan = findLoan(loanId);
        IncentiveOffer offer = findOffer(findPlan(loan));
        try {
            offer.accept(dateOrToday(request));
        } finally {
            loans.save(loan); // an expired offer is marked EXPIRED even though the accept is refused
        }
        return OfferView.of(offer);
    }

    /** R3: borrower declines the offer. Optional body: {"date": "2026-11-15"} (defaults to today). */
    @PostMapping("/loans/{loanId}/plan/offer/decline")
    public OfferView declineOffer(@PathVariable String loanId, @RequestBody(required = false) DateRequest request) {
        Loan loan = findLoan(loanId);
        IncentiveOffer offer = findOffer(findPlan(loan));
        try {
            offer.decline(dateOrToday(request));
        } finally {
            loans.save(loan);
        }
        return OfferView.of(offer);
    }

    /** R4: approve the plan if it passes regulatory validation. */
    @PostMapping("/loans/{loanId}/plan/approve")
    public Map<String, Object> approvePlan(@PathVariable String loanId) {
        Loan loan = findLoan(loanId);
        RepaymentPlan plan = findPlan(loan);
        boolean approved = plan.approve(validator);
        loans.save(loan);
        return Map.of("approved", approved, "plan", PlanView.of(plan));
    }

    // ---------- helpers ----------

    private Loan findLoan(String loanId) {
        return loans.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
    }

    private static RepaymentPlan findPlan(Loan loan) {
        if (loan.getActivePlan() == null) {
            throw new IllegalStateException("Loan " + loan.getLoanId() + " has no repayment plan yet");
        }
        return loan.getActivePlan();
    }

    private static IncentiveOffer findOffer(RepaymentPlan plan) {
        if (plan.getIncentiveOffer() == null) {
            throw new IllegalStateException("This plan has no incentive offer yet");
        }
        return plan.getIncentiveOffer();
    }

    private static LocalDate dateOrToday(DateRequest request) {
        return (request == null || request.date() == null) ? LocalDate.now() : request.date();
    }

    // ---------- errors -> HTTP status codes ----------

    static class LoanNotFoundException extends RuntimeException {
        LoanNotFoundException(String loanId) {
            super("Unknown loan: " + loanId);
        }
    }

    @ExceptionHandler(LoanNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(LoanNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleRuleViolation(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}

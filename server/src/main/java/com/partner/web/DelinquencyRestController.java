package com.partner.web;

import com.partner.data.LoanDatabase;
import com.partner.domain.Delinquency;
import com.partner.domain.DelinquencyPolicy;
import com.partner.domain.DelinquencyStatus;
import com.partner.domain.Loan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * REST (web) entry point for delinquency detection. It does not contain any delinquency rules:
 * it delegates to DelinquencyController.checkDelinquency, which applies DelinquencyPolicy to the
 * loan's payment history and saves the updated Delinquency record.
 *
 * The optional ?date=YYYY-MM-DD parameter evaluates the loan "as of" that date (default: today),
 * so a demo can show the six-month boundary without waiting for the calendar.
 */
@RestController
@RequestMapping("/api/delinquency")
public class DelinquencyRestController {

    private final DelinquencyController delinquencyController;
    private final LoanDatabase loanDatabase;

    public DelinquencyRestController(DelinquencyController delinquencyController, LoanDatabase loanDatabase) {
        this.delinquencyController = delinquencyController;
        this.loanDatabase = loanDatabase;
    }

    /** JSON shape returned for one loan. */
    public record DelinquencyView(
            String loanId,
            LocalDate evaluatedOn,
            DelinquencyStatus status,
            BigDecimal initialLoanAmount,
            int missedPayments,
            BigDecimal amountPastDue,
            BigDecimal unpaidThreshold,
            long maxDaysPastDue,
            boolean sixMonthsPastDue,
            LocalDate delinquentSince) {
    }

    @GetMapping("/loans")
    public List<DelinquencyView> checkAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate asOf = date != null ? date : LocalDate.now();
        return loanDatabase.findAll().stream()
                .map(loan -> check(loan.getLoanId(), asOf))
                .toList();
    }

    @GetMapping("/loans/{loanId}")
    public DelinquencyView checkOne(
            @PathVariable String loanId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return check(loanId, date != null ? date : LocalDate.now());
    }

    private DelinquencyView check(String loanId, LocalDate asOf) {
        DelinquencyStatus status;
        try {
            status = delinquencyController.checkDelinquency(loanId, asOf);
        } catch (IllegalArgumentException e) { // "Unknown loan: ..."
            throw new LoanNotFoundException(e.getMessage());
        }
        Loan loan = loanDatabase.findById(loanId).orElseThrow();
        Delinquency active = loan.getActiveDelinquency();
        return new DelinquencyView(
                loan.getLoanId(),
                asOf,
                status,
                loan.getInitialLoanAmount(),
                loan.checkMissedPayments(asOf).size(),
                loan.calculateAmountPastDue(asOf),
                loan.getInitialLoanAmount().multiply(DelinquencyPolicy.UNPAID_INITIAL_FRACTION_THRESHOLD)
                        .setScale(2, RoundingMode.HALF_UP),
                loan.calculateMaxDaysPastDue(asOf),
                loan.hasInstallmentAtLeastSixMonthsPastDue(asOf),
                active != null && active.isOpen() ? active.getStartDate() : null);
    }

    /** Thrown when DelinquencyController reports an unknown loan id. */
    static class LoanNotFoundException extends RuntimeException {
        LoanNotFoundException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(LoanNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUnknownLoan(LoanNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    /** ?date= that is not YYYY-MM-DD. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleBadParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid value for '" + e.getName() + "'; dates must be YYYY-MM-DD"));
    }
}

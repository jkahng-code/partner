package com.partner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partner.data.InMemoryRepaymentLoanDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the repayment API: request -> controller -> domain -> in-memory store -> JSON.
 * Expected values come from the test basis (R1-R5, I1-I4), the same as the A2 black-box tests.
 * Seed data: L-100 = $1,000.00 delinquent; L-200 = $1,200.00 not delinquent; L-300 = $15,000.00 delinquent.
 */
@WebMvcTest(
        controllers = RepaymentController.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
        })
@Import(InMemoryRepaymentLoanDatabase.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh seed data per test
class RepaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test // API-1: seed data is listed
    void listLoans_returnsSeededLoans() throws Exception {
        mockMvc.perform(get("/api/repayment/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].loanId").value("L-100"));
    }

    @Test // API-2 (R1): valid term creates a plan; $1,000 / 10 = $100.00
    void createPlan_validTerm_returns201WithMonthlyAmount() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.termMonths").value(10))
                .andExpect(jsonPath("$.monthlyAmount").value(100.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test // API-3 (R1 boundary): term 0 is rejected with 400 and no plan is created
    void createPlan_termZero_returns400AndNoPlan() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        mockMvc.perform(get("/api/repayment/loans/L-100"))
                .andExpect(jsonPath("$.activePlan").doesNotExist());
    }

    @Test // API-4 (I3): a second plan on the same loan is refused with 409
    void createPlan_secondPlan_returns409() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":6}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":12}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/repayment/loans/L-100"))
                .andExpect(jsonPath("$.activePlan.termMonths").value(6));
    }

    @Test // API-5: unknown loan returns 404
    void unknownLoan_returns404() throws Exception {
        mockMvc.perform(get("/api/repayment/loans/L-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown loan: L-999"));
    }

    @Test // API-6 (R2, R3, I2, I4): offer amounts, then accepting ON the expiration date succeeds
    void offerAndAcceptOnExpirationDate_succeeds() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":10}"));
        mockMvc.perform(post("/api/repayment/loans/L-100/plan/offer").contentType(JSON).content("{\"date\":\"2026-11-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reducedPaymentAmount").value(75.00))   // 75% of $100.00
                .andExpect(jsonPath("$.catchUpPaymentAmount").value(110.72))  // ($1,000 - $225) / 7, rounded up
                .andExpect(jsonPath("$.totalScheduled").value(1000.04))
                .andExpect(jsonPath("$.expirationDate").value("2026-11-15"));
        mockMvc.perform(post("/api/repayment/loans/L-100/plan/offer/accept").contentType(JSON).content("{\"date\":\"2026-11-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test // API-7 (R5, I2): accepting the day after expiration is refused and the offer is EXPIRED
    void acceptAfterExpiration_returns409AndExpires() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":10}"));
        mockMvc.perform(post("/api/repayment/loans/L-100/plan/offer").contentType(JSON).content("{\"date\":\"2026-11-01\"}"));
        mockMvc.perform(post("/api/repayment/loans/L-100/plan/offer/accept").contentType(JSON).content("{\"date\":\"2026-11-16\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/repayment/loans/L-100"))
                .andExpect(jsonPath("$.activePlan.incentiveOffer.status").value("EXPIRED"));
    }

    @Test // API-8 (R2): a borrower who is not delinquent cannot get an offer
    void offerForNonDelinquentBorrower_returns409() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-200/plan").contentType(JSON).content("{\"termMonths\":12}"));
        mockMvc.perform(post("/api/repayment/loans/L-200/plan/offer").contentType(JSON).content("{\"date\":\"2026-11-01\"}"))
                .andExpect(status().isConflict());
    }

    @Test // API-9 (R4, I1): a plan within the $5,000 cap is approved
    void approveWithinCap_approved() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-100/plan").contentType(JSON).content("{\"termMonths\":10}"));
        mockMvc.perform(post("/api/repayment/loans/L-100/plan/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.plan.status").value("APPROVED"));
    }

    @Test // API-10 (R4, I1): $15,000 over 2 months = $7,500/month, over the cap, so not approved
    void approveOverCap_notApproved() throws Exception {
        mockMvc.perform(post("/api/repayment/loans/L-300/plan").contentType(JSON).content("{\"termMonths\":2}"));
        mockMvc.perform(post("/api/repayment/loans/L-300/plan/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.plan.status").value("PENDING"));
    }
}

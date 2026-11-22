package com.partner.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partner.config.DelinquencyConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the delinquency-detection API (test basis R1-R5 through the web layer).
 * Seed data is described in DelinquencyConfig; every loan has a $10,000.00 principal.
 */
@WebMvcTest(
        controllers = DelinquencyRestController.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
        })
@Import(DelinquencyConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh seed data per test
class DelinquencyRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String AS_OF = "?date=2026-11-22";

    @Test // DQ-API-1: list evaluates every seeded loan
    void listsAllLoansWithStatus() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans" + AS_OF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].loanId").value("D-100"))
                .andExpect(jsonPath("$[0].status").value("NON_DELINQUENT"))
                .andExpect(jsonPath("$[1].status").value("DELINQUENT"))
                .andExpect(jsonPath("$[2].status").value("DELINQUENT"))
                .andExpect(jsonPath("$[3].status").value("NON_DELINQUENT"))
                .andExpect(jsonPath("$[4].status").value("NON_DELINQUENT"));
    }

    @Test // DQ-API-2: late but under both thresholds is not delinquent
    void latePaymentUnderThresholds_notDelinquent() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-100" + AS_OF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NON_DELINQUENT"))
                .andExpect(jsonPath("$.missedPayments").value(1))
                .andExpect(jsonPath("$.amountPastDue").value(500.00))
                .andExpect(jsonPath("$.maxDaysPastDue").value(113))
                .andExpect(jsonPath("$.sixMonthsPastDue").value(false))
                .andExpect(jsonPath("$.delinquentSince").value(nullValue()));
    }

    @Test // DQ-API-3: unpaid amount over 25% of principal is delinquent
    void unpaidOverQuarter_delinquent() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-200" + AS_OF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELINQUENT"))
                .andExpect(jsonPath("$.amountPastDue").value(2600.00))
                .andExpect(jsonPath("$.unpaidThreshold").value(2500.00))
                .andExpect(jsonPath("$.maxDaysPastDue").value(52))
                .andExpect(jsonPath("$.sixMonthsPastDue").value(false))
                .andExpect(jsonPath("$.delinquentSince").value("2026-11-22"));
    }

    @Test // DQ-API-4: boundary - exactly 25% unpaid is NOT delinquent (rule is "more than")
    void unpaidExactlyQuarter_notDelinquent() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-500" + AS_OF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NON_DELINQUENT"))
                .andExpect(jsonPath("$.amountPastDue").value(2500.00));
    }

    @Test // DQ-API-5: boundary - one day before six months past due is not delinquent
    void dayBeforeSixMonths_notDelinquent() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-300?date=2026-10-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NON_DELINQUENT"))
                .andExpect(jsonPath("$.maxDaysPastDue").value(182))
                .andExpect(jsonPath("$.sixMonthsPastDue").value(false));
    }

    @Test // DQ-API-6: boundary - exactly six calendar months past due is delinquent
    void exactlySixMonths_delinquent() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-300?date=2026-10-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELINQUENT"))
                .andExpect(jsonPath("$.maxDaysPastDue").value(183))
                .andExpect(jsonPath("$.sixMonthsPastDue").value(true))
                .andExpect(jsonPath("$.delinquentSince").value("2026-10-15"));
    }

    @Test // DQ-API-7: the open delinquency keeps its start date on later checks
    void laterCheck_keepsOriginalStartDate() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-300?date=2026-10-15"));
        mockMvc.perform(get("/api/delinquency/loans/D-300" + AS_OF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELINQUENT"))
                .andExpect(jsonPath("$.maxDaysPastDue").value(221))
                .andExpect(jsonPath("$.delinquentSince").value("2026-10-15"));
    }

    @Test // DQ-API-8: installment paid in full has nothing missed
    void paidInFull_notDelinquent() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-400" + AS_OF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NON_DELINQUENT"))
                .andExpect(jsonPath("$.missedPayments").value(0))
                .andExpect(jsonPath("$.maxDaysPastDue").value(0));
    }

    @Test // DQ-API-9: unknown loan is 404 with an error message
    void unknownLoan_404() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/NOPE" + AS_OF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown loan: NOPE"));
    }

    @Test // DQ-API-10: badly formatted date is rejected with 400
    void badDate_400() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-100?date=11-22-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for 'date'; dates must be YYYY-MM-DD"));
    }

    @Test // DQ-API-11: no date means "evaluate as of today"
    void noDate_defaultsToToday() throws Exception {
        mockMvc.perform(get("/api/delinquency/loans/D-400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedOn").value(LocalDate.now().toString()));
    }
}

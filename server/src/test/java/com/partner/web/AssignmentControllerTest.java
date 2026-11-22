package com.partner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.partner.data.InMemoryLoanOfficerDatabase;
import com.partner.data.InMemoryRepaymentLoanDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the officer-assignment API.
 * Seed data: officers O-1 (Dana Lee) and O-2 (Marcus Reed); borrowers B-100 (delinquent, loan L-100),
 * B-200 (not delinquent, loan L-200), B-300 (delinquent, loan L-300).
 */
@WebMvcTest(
        controllers = AssignmentController.class,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
        })
@Import({InMemoryLoanOfficerDatabase.class, InMemoryRepaymentLoanDatabase.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // fresh seed data per test
class AssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private void assign(String officerId, String borrowerId) throws Exception {
        mockMvc.perform(post("/api/officers/" + officerId + "/borrowers")
                .contentType(JSON).content("{\"borrowerId\":\"" + borrowerId + "\"}"));
    }

    @Test // OA-API-1: officers are listed with empty caseloads
    void listOfficers_returnsSeededOfficers() throws Exception {
        mockMvc.perform(get("/api/officers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].officerId").value("O-1"))
                .andExpect(jsonPath("$[0].caseloadSize").value(0))
                .andExpect(jsonPath("$[0].maxCaseload").value(25));
    }

    @Test // OA-API-2 (R6): assigning a delinquent borrower returns 201 and updates the caseload
    void assignDelinquentBorrower_returns201() throws Exception {
        mockMvc.perform(post("/api/officers/O-1/borrowers").contentType(JSON).content("{\"borrowerId\":\"B-100\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseloadSize").value(1))
                .andExpect(jsonPath("$.borrowerIds[0]").value("B-100"));
    }

    @Test // OA-API-3 (R6): a borrower who is not delinquent is refused with 409
    void assignNonDelinquentBorrower_returns409() throws Exception {
        mockMvc.perform(post("/api/officers/O-1/borrowers").contentType(JSON).content("{\"borrowerId\":\"B-200\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test // OA-API-4 (I5): a borrower already owned by another officer is refused with 409
    void assignBorrowerOwnedByAnotherOfficer_returns409() throws Exception {
        assign("O-1", "B-100");
        mockMvc.perform(post("/api/officers/O-2/borrowers").contentType(JSON).content("{\"borrowerId\":\"B-100\"}"))
                .andExpect(status().isConflict());
    }

    @Test // OA-API-5 (R7): the assigned officer can view the borrower's case, including loans
    void viewOwnBorrower_returns200WithLoans() throws Exception {
        assign("O-1", "B-100");
        mockMvc.perform(get("/api/officers/O-1/borrowers/B-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ava Martinez"))
                .andExpect(jsonPath("$.assignedOfficerId").value("O-1"))
                .andExpect(jsonPath("$.loans[0].loanId").value("L-100"));
    }

    @Test // OA-API-6 (R7): another officer is refused with 403
    void viewAnotherOfficersBorrower_returns403() throws Exception {
        assign("O-1", "B-100");
        mockMvc.perform(get("/api/officers/O-2/borrowers/B-100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Officer O-2 is not assigned to borrower B-100"));
    }

    @Test // OA-API-7 (R6, R7): unassigning frees the slot and removes access
    void unassign_thenViewIsForbidden() throws Exception {
        assign("O-1", "B-100");
        mockMvc.perform(delete("/api/officers/O-1/borrowers/B-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseloadSize").value(0));
        mockMvc.perform(get("/api/officers/O-1/borrowers/B-100"))
                .andExpect(status().isForbidden());
    }

    @Test // OA-API-8: unknown officer or borrower returns 404
    void unknownOfficerOrBorrower_returns404() throws Exception {
        mockMvc.perform(get("/api/officers/O-9/borrowers/B-100"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/officers/O-1/borrowers").contentType(JSON).content("{\"borrowerId\":\"B-999\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown borrower: B-999"));
    }
}

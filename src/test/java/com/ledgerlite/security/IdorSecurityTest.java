package com.ledgerlite.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgerlite.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Insecure Direct Object Reference checks: a user must never reach another user's money or history. */
class IdorSecurityTest extends AbstractIntegrationTest {

    @Test
    void aUserCannotReadAnotherUsersAccount() throws Exception {
        TestUser alice = newUser("alice");
        TestUser mallory = newUser("mallory");
        String aliceAccountId = createAccount(alice, "Private", "USD", "100.00").get("id").asText();

        mockMvc.perform(get("/api/accounts/" + aliceAccountId).cookie(auth(mallory)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // The owner still can.
        mockMvc.perform(get("/api/accounts/" + aliceAccountId).cookie(auth(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void aUserCannotSpendFromAnotherUsersAccount() throws Exception {
        TestUser alice = newUser("alice");
        TestUser mallory = newUser("mallory");
        String aliceAccountId = createAccount(alice, "Private", "USD", "100.00").get("id").asText();
        String malloryNumber = createAccount(mallory, "Loot", "USD", null).get("accountNumber").asText();

        transfer(mallory, "idor-" + UUID.randomUUID(), aliceAccountId, malloryNumber, "50.00")
                .andExpect(status().isForbidden());

        assertThat(balanceOf(alice, aliceAccountId)).isEqualTo("100.0000");
    }

    @Test
    void aUserCannotReadATransactionThatDoesNotTouchTheirAccounts() throws Exception {
        TestUser victim = newUser("victim");
        TestUser friend = newUser("friend");
        TestUser outsider = newUser("outsider");
        String victimAccountId = createAccount(victim, "V", "USD", "100.00").get("id").asText();
        String friendNumber = createAccount(friend, "F", "USD", null).get("accountNumber").asText();

        MvcResult sent = transfer(victim, "idor-" + UUID.randomUUID(), victimAccountId, friendNumber, "10.00")
                .andExpect(status().isCreated())
                .andReturn();
        String txId = objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/transactions/" + txId).cookie(auth(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/transactions/" + txId).cookie(auth(victim))).andExpect(status().isOk());
        mockMvc.perform(get("/api/transactions/" + txId).cookie(auth(friend))).andExpect(status().isOk());
    }

    @Test
    void historyFilteredByAnotherUsersAccountIsForbidden() throws Exception {
        TestUser alice = newUser("alice");
        TestUser mallory = newUser("mallory");
        String aliceAccountId = createAccount(alice, "Private", "USD", "100.00").get("id").asText();

        mockMvc.perform(get("/api/transactions").param("accountId", aliceAccountId).cookie(auth(mallory)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/accounts").cookie(new Cookie("LL_TOKEN", "not-a-real-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAccountListContainsOnlyTheCallersOwnAccounts() throws Exception {
        TestUser alice = newUser("alice");
        TestUser mallory = newUser("mallory");
        JsonNode aliceAccount = createAccount(alice, "Private", "USD", "100.00");

        mockMvc.perform(get("/api/accounts").cookie(auth(mallory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/accounts").cookie(auth(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceAccount.get("id").asText()));
    }
}

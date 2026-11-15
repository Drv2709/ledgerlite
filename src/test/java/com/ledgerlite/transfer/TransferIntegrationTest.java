package com.ledgerlite.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgerlite.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The transfer endpoints against a real PostgreSQL, through the real security filter chain. */
class TransferIntegrationTest extends AbstractIntegrationTest {

    @Test
    void transferMovesMoneyAndWritesABalancedPairOfLedgerEntries() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode from = createAccount(alice, "Main", "USD", "100.00");
        JsonNode to = createAccount(bob, "Main", "USD", null);
        String fromId = from.get("id").asText();
        String toId = to.get("id").asText();

        transfer(alice, key(), fromId, to.get("accountNumber").asText(), "25.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("25.0000"))
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].accountId").value(fromId))
                .andExpect(jsonPath("$.entries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.entries[1].accountId").value(toId));

        assertThat(balanceOf(alice, fromId)).isEqualTo("75.0000");
        assertThat(balanceOf(bob, toId)).isEqualTo("25.0000");
    }

    @Test
    void retryWithTheSameIdempotencyKeyMovesMoneyOnlyOnce() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode from = createAccount(alice, "Main", "USD", "100.00");
        JsonNode to = createAccount(bob, "Main", "USD", null);
        String fromId = from.get("id").asText();
        String toNumber = to.get("accountNumber").asText();
        String sameKey = key();

        MvcResult first = transfer(alice, sameKey, fromId, toNumber, "30.00")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn();
        String firstBody = first.getResponse().getContentAsString();

        transfer(alice, sameKey, fromId, toNumber, "30.00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(content().string(firstBody));

        assertThat(balanceOf(alice, fromId)).isEqualTo("70.0000");
        assertThat(balanceOf(bob, to.get("id").asText())).isEqualTo("30.0000");

        // Same key with a different amount is a client bug, not a retry.
        transfer(alice, sameKey, fromId, toNumber, "31.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(balanceOf(alice, fromId)).isEqualTo("70.0000");
    }

    @Test
    void overdraftIsRejectedAndLeavesTheBalanceUntouched() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode from = createAccount(alice, "Main", "USD", "100.00");
        JsonNode to = createAccount(bob, "Main", "USD", null);
        String fromId = from.get("id").asText();

        transfer(alice, key(), fromId, to.get("accountNumber").asText(), "100.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(alice, fromId)).isEqualTo("100.0000");
        assertThat(balanceOf(bob, to.get("id").asText())).isEqualTo("0.0000");
    }

    @Test
    void reversalRestoresBothBalancesAndCannotBeRepeated() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode from = createAccount(alice, "Main", "USD", "100.00");
        JsonNode to = createAccount(bob, "Main", "USD", null);
        String fromId = from.get("id").asText();
        String toId = to.get("id").asText();

        MvcResult sent = transfer(alice, key(), fromId, to.get("accountNumber").asText(), "40.00")
                .andExpect(status().isCreated())
                .andReturn();
        String txId = objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText();

        reverse(alice, key(), txId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.reversesTransactionId").value(txId));

        assertThat(balanceOf(alice, fromId)).isEqualTo("100.0000");
        assertThat(balanceOf(bob, toId)).isEqualTo("0.0000");

        reverse(alice, key(), txId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVERSED"));
        assertThat(balanceOf(alice, fromId)).isEqualTo("100.0000");
    }

    @Test
    void aTransferWithoutACsrfTokenIsForbiddenAndMovesNothing() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode from = createAccount(alice, "Main", "USD", "100.00");
        JsonNode to = createAccount(bob, "Main", "USD", null);
        String fromId = from.get("id").asText();
        String body = "{\"sourceAccountId\":\"%s\",\"destinationAccountNumber\":\"%s\",\"amount\":\"10.00\"}"
                .formatted(fromId, to.get("accountNumber").asText());

        // Deliberately no .with(csrf()).
        mockMvc.perform(post("/api/transfers")
                        .cookie(auth(alice))
                        .header("Idempotency-Key", key())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        assertThat(balanceOf(alice, fromId)).isEqualTo("100.0000");
    }

    @Test
    void aTransferWithoutAnIdempotencyKeyIsRejected() throws Exception {
        TestUser alice = newUser("alice");
        TestUser bob = newUser("bob");
        JsonNode from = createAccount(alice, "Main", "USD", "100.00");
        JsonNode to = createAccount(bob, "Main", "USD", null);

        transfer(alice, null, from.get("id").asText(), to.get("accountNumber").asText(), "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------------------------- helpers

    private static String key() {
        return "it-" + UUID.randomUUID();
    }

    private ResultActions reverse(TestUser user, String idempotencyKey, String transactionId) throws Exception {
        return mockMvc.perform(post("/api/transactions/" + transactionId + "/reverse")
                .cookie(auth(user)).with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
    }
}

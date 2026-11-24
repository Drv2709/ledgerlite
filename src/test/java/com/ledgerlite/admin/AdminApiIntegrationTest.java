package com.ledgerlite.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgerlite.auth.AuthService;
import com.ledgerlite.auth.dto.LoginRequest;
import com.ledgerlite.auth.dto.RegisterRequest;
import com.ledgerlite.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin API on a real PostgreSQL: who may call it, what it returns, and that it cannot change anything. */
class AdminApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired private JdbcTemplate jdbcTemplate;

    private record Registered(UUID id, String email, String token) {}

    // ---------------------------------------------------------------- who may call it

    @Test
    void anonymousCallersGetUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void normalUsersGetForbidden() throws Exception {
        TestUser user = newUser("regular");

        mockMvc.perform(get("/api/admin/users").cookie(auth(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/admin/transactions").cookie(auth(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenIssuedBeforePromotionStaysForbiddenUntilTheUserSignsInAgain() throws Exception {
        Registered user = register("promoted");
        setRole(user.id(), "ADMIN");

        // The role is baked into the JWT at sign-in, so the old token still says USER.
        mockMvc.perform(get("/api/admin/users").cookie(cookie(user.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users").cookie(cookie(loginToken(user))))
                .andExpect(status().isOk());
    }

    @Test
    void aDemotedAdminLosesAccessImmediatelyEvenWithAValidToken() throws Exception {
        Registered admin = register("admin");
        setRole(admin.id(), "ADMIN");
        String token = loginToken(admin);

        mockMvc.perform(get("/api/admin/users").cookie(cookie(token))).andExpect(status().isOk());

        setRole(admin.id(), "USER");   // token still carries ROLE_ADMIN and has not expired

        mockMvc.perform(get("/api/admin/users").cookie(cookie(token)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- what it returns

    @Test
    void adminListsUsersWithoutHashesTokensOrTheSystemUser() throws Exception {
        Registered regular = register("regular");
        String admin = adminToken();

        MvcResult result = mockMvc.perform(get("/api/admin/users").param("size", "50").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> emails = new ArrayList<>();
        objectMapper.readTree(body).get("content").forEach(node -> emails.add(node.get("email").asText()));

        assertThat(emails).contains(regular.email());
        assertThat(emails).doesNotContain("system@ledgerlite.internal");
        assertThat(body).doesNotContain("password").doesNotContain("$2a$").doesNotContain("LL_TOKEN");

        mockMvc.perform(get("/api/admin/users").param("size", "1").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void adminSeesTransactionsOfAllUsersWithAccountNumbers() throws Exception {
        Registered alice = register("alice");
        Registered bob = register("bob");
        JsonNode aliceAccount = createAccount(asUser(alice), "Main", "USD", "100.00");
        JsonNode bobAccount = createAccount(asUser(bob), "Main", "USD", null);
        String aliceId = aliceAccount.get("id").asText();
        String aliceNumber = aliceAccount.get("accountNumber").asText();
        String bobId = bobAccount.get("id").asText();
        String bobNumber = bobAccount.get("accountNumber").asText();

        transfer(asUser(alice), "admin-" + UUID.randomUUID(), aliceId, bobNumber, "10.00")
                .andExpect(status().isCreated());

        String admin = adminToken();

        // Alice's account: the newest first (the transfer), then her opening deposit from the SYSTEM account.
        MvcResult forAlice = mockMvc.perform(get("/api/admin/transactions")
                        .param("accountId", aliceId).cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].amount").value("10.0000"))
                .andExpect(jsonPath("$.content[0].sourceAccountNumber").value(aliceNumber))
                .andExpect(jsonPath("$.content[0].destinationAccountNumber").value(bobNumber))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[1].sourceAccountNumber").value("LL-SYSTEM-USD"))
                .andReturn();
        assertThat(forAlice.getResponse().getContentAsString()).doesNotContain("entries");

        // Filters: type, and the other party's account.
        mockMvc.perform(get("/api/admin/transactions")
                        .param("accountId", aliceId).param("type", "TRANSFER").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/transactions").param("accountId", bobId).cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"));
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        String admin = adminToken();

        mockMvc.perform(get("/api/admin/transactions").param("size", "0").cookie(cookie(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/transactions").param("size", "101").cookie(cookie(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/transactions").param("page", "-1").cookie(cookie(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/transactions").param("type", "BOGUS").cookie(cookie(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/transactions")
                        .param("from", "2030-01-02T00:00:00Z").param("to", "2030-01-01T00:00:00Z")
                        .cookie(cookie(admin)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- what it must NOT do

    @Test
    void anAdminStillCannotReadAnotherUsersAccountThroughTheNormalApi() throws Exception {
        Registered alice = register("alice");
        String aliceAccountId = createAccount(asUser(alice), "Private", "USD", "50.00").get("id").asText();
        String admin = adminToken();

        mockMvc.perform(get("/api/accounts/" + aliceAccountId).cookie(cookie(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void thereAreNoWriteEndpointsEvenForAdmins() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/users").cookie(cookie(admin)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/admin/transactions").cookie(cookie(admin)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------------------------------------------------------------- helpers

    private Registered register(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@test.example";
        AuthService.AuthResult result = authService.register(new RegisterRequest(email, PASSWORD));
        return new Registered(result.user().id(), email, result.token());
    }

    private TestUser asUser(Registered registered) {
        return new TestUser(registered.id(), registered.token());
    }

    /** Promotion is a database operation. There is deliberately no API for it. */
    private void setRole(UUID userId, String role) {
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", role, userId);
    }

    /** Signing in again issues a token that carries the user's CURRENT role. */
    private String loginToken(Registered user) {
        return authService.login(new LoginRequest(user.email(), PASSWORD)).token();
    }

    private String adminToken() {
        Registered admin = register("admin");
        setRole(admin.id(), "ADMIN");
        return loginToken(admin);
    }

    private Cookie cookie(String token) {
        return new Cookie("LL_TOKEN", token);
    }
}

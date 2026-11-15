package com.ledgerlite.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerlite.auth.AuthService;
import com.ledgerlite.auth.dto.RegisterRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that need the whole application on a REAL PostgreSQL (Testcontainers).
 * One container is shared by every integration test class. Each test creates its own fresh users
 * and accounts, so tests never depend on each other (the ledger is append-only and is never cleaned).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();   // Testcontainers' Ryuk sidecar removes it when the JVM exits
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected AuthService authService;

    public record TestUser(UUID id, String token) {}

    /** Registers a brand-new user through the real service (real BCrypt, real JWT). */
    protected TestUser newUser(String prefix) {
        AuthService.AuthResult result = authService.register(
                new RegisterRequest(prefix + "-" + UUID.randomUUID() + "@test.example", "Str0ng!Passw0rd"));
        return new TestUser(result.user().id(), result.token());
    }

    /** The real auth cookie, so requests go through the real JwtAuthenticationFilter. */
    protected Cookie auth(TestUser user) {
        return new Cookie("LL_TOKEN", user.token());
    }

    protected JsonNode createAccount(TestUser user, String name, String currency, String deposit) throws Exception {
        String body = deposit == null
                ? "{\"name\":\"%s\",\"currency\":\"%s\"}".formatted(name, currency)
                : "{\"name\":\"%s\",\"currency\":\"%s\",\"initialDeposit\":\"%s\"}".formatted(name, currency, deposit);
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .cookie(auth(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected ResultActions transfer(TestUser user, String idempotencyKey, String sourceAccountId,
                                     String destinationNumber, String amount) throws Exception {
        String body = "{\"sourceAccountId\":\"%s\",\"destinationAccountNumber\":\"%s\",\"amount\":\"%s\"}"
                .formatted(sourceAccountId, destinationNumber, amount);
        MockHttpServletRequestBuilder request = post("/api/transfers")
                .cookie(auth(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    protected String balanceOf(TestUser user, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/accounts/" + accountId).cookie(auth(user)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("balance").asText();
    }
}

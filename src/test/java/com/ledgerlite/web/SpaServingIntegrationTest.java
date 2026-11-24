package com.ledgerlite.web;

import com.ledgerlite.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The single-image deployment serves the React build from the same origin as the API.
 * src/test/resources/static holds a tiny stand-in for the real build.
 */
class SpaServingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void theRootPathServesTheReactIndexPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"root\"")));
    }

    @Test
    void clientSideRoutesFallBackToIndexHtml() throws Exception {
        for (String path : List.of("/login", "/register", "/transactions", "/transfer", "/some/unknown/route")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"root\"")));
        }
    }

    @Test
    void hashedAssetsAreServedAsIsAndCachedForAYear() throws Exception {
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-test-asset")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void aMissingFileIsARealNotFoundNotTheIndexPage() throws Exception {
        mockMvc.perform(get("/assets/missing.js"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("id=\"root\""))));
        mockMvc.perform(get("/missing.png"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("id=\"root\""))));
    }

    @Test
    void unknownApiPathsAreNeverAnsweredWithHtml() throws Exception {
        // Anonymous: the API rule rejects it before any routing.
        mockMvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());

        // Signed in: no such endpoint, so a 404 that is not the React page.
        TestUser user = newUser("spa");
        mockMvc.perform(get("/api/does-not-exist").cookie(auth(user)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("id=\"root\""))));
    }

    @Test
    void healthIsStillServedByTheApi() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

package dev.danvega.bank.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AssistantLiveTests {

    @Autowired
    MockMvc mvc;

    @Test
    void explainsTheDoubleChargeThenProposesButDoesNotOpen() throws Exception {
        chat("Why was I charged twice at Acme Coffee?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("acme")));
        mvc.perform(get("/api/disputes")).andExpect(jsonPath("$.disputes[?(@.status == 'PENDING_CONFIRMATION')]").isEmpty());

        chat("Yes, please dispute the second charge.")
                .andExpect(status().isOk());

        mvc.perform(get("/api/disputes"))
                .andExpect(jsonPath("$.disputes[?(@.status == 'PENDING_CONFIRMATION')]").isNotEmpty())
                .andExpect(jsonPath("$.disputes[?(@.status == 'OPEN')]").isEmpty());
    }

    private ResultActions chat(String message) throws Exception {
        return mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"live-1","message":"%s"}""".formatted(message)))
                .andDo(print());
    }

}

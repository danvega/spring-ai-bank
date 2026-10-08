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

/** The live script for demo 2, against the real model. Runs only when OPENAI_API_KEY is set. */
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
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("acme")))
                .andExpect(jsonPath("$.awaitingConfirmation").isEmpty());

        chat("Yes, please dispute the second charge.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awaitingConfirmation[0].transactionId").exists())
                .andExpect(jsonPath("$.awaitingConfirmation[0].status").value("PENDING_CONFIRMATION"));

        // Nothing of Maria's was opened by the model
        mvc.perform(get("/api/disputes"))
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

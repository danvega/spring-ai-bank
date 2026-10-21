package dev.danvega.bank.memory;

import java.util.List;

import dev.danvega.bank.FakeOpenAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShortTermMemoryControllerTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FakeOpenAi.register(registry);
    }

    private final FakeOpenAi openAi = FakeOpenAi.instance();

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        openAi.reset();
    }

    @Test
    void theSecondCallReplaysTheFirst() throws Exception {
        openAi.replyWithText("Hi Maria.").replyWithText("Your name is Maria.");

        chat("s1", "Hi, my name is Maria.");
        chat("s1", "What's my name?");

        JsonNode second = openAi.lastRequest();
        assertThat(userMessages(second)).containsExactly("Hi, my name is Maria.", "What's my name?");
        assertThat(second.get("input").toString()).contains("Hi Maria.");
        mvc.perform(get("/api/memory/conversations/s1"))
                .andExpect(jsonPath("$", contains("USER: Hi, my name is Maria.", "ASSISTANT: Hi Maria.",
                        "USER: What's my name?", "ASSISTANT: Your name is Maria.")));
    }

    @Test
    void aNewConversationStartsEmpty() throws Exception {
        openAi.replyWithText("Hi Maria.").replyWithText("I don't know your name.");

        chat("s1", "Hi, my name is Maria.");
        chat("s2", "What's my name?");

        assertThat(userMessages(openAi.lastRequest())).containsExactly("What's my name?");
    }

    @Test
    void theWindowKeepsTheLastTenMessages() throws Exception {
        for (int i = 1; i <= 6; i++) {
            openAi.replyWithText("Answer " + i);
        }
        for (int i = 1; i <= 6; i++) {
            chat("s1", "Question " + i);
        }

        mvc.perform(get("/api/memory/conversations/s1"))
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0]").value("USER: Question 2"));
    }

    @Test
    void clearDeletesTheConversation() throws Exception {
        openAi.replyWithText("Hi Maria.");
        chat("s1", "Hi, my name is Maria.");

        mvc.perform(delete("/api/memory/conversations/s1")).andExpect(status().isOk());

        mvc.perform(get("/api/memory/conversations/s1")).andExpect(jsonPath("$", hasSize(0)));
    }

    private ResultActions chat(String conversationId, String message) throws Exception {
        return mvc.perform(post("/api/memory/conversations/{id}/chat", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message":"%s"}""".formatted(message)))
                .andExpect(status().isOk());
    }

    private static List<String> userMessages(JsonNode request) {
        return request.get("input").valueStream()
                .filter(item -> "user".equals(item.path("role").asString("")))
                .map(ShortTermMemoryControllerTests::text)
                .toList();
    }

    private static String text(JsonNode item) {
        JsonNode content = item.get("content");
        if (content.isString()) {
            return content.asString();
        }
        return content.get(0).get("text").asString();
    }

}

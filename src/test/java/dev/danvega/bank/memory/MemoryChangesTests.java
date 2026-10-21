package dev.danvega.bank.memory;

import java.util.List;

import dev.danvega.bank.FakeOpenAi;
import dev.danvega.bank.FakeOpenAi.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MemoryChangesTests {

    @DynamicPropertySource
    static void openAi(DynamicPropertyRegistry registry) {
        FakeOpenAi.register(registry);
    }

    private final FakeOpenAi openAi = FakeOpenAi.instance();

    @Autowired
    MockMvc mvc;

    @Autowired
    ChatMemory chatMemory;

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @BeforeEach
    void reset() {
        openAi.reset();
    }

    @Test
    void aConversationIdIsRequired() {
        ChatClient chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        assertThatThrownBy(() -> chatClient.prompt().user("Hi").call().content())
                .hasMessageContaining("conversationId cannot be null");
        assertThat(openAi.requests()).isEmpty();
    }

    @Test
    void memorySavesTheFinalExchangeNotTheToolLoop() throws Exception {
        openAi.replyWithToolCalls(ToolCall.of("findDuplicateCharges", "{}"))
                .replyWithText("You were charged twice at Acme Coffee.");

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"c1","message":"Why was I charged twice?"}"""))
                .andExpect(status().isOk());

        assertThat(openAi.requests()).hasSize(2);
        List<Message> saved = chatMemory.get("C-100:c1");
        assertThat(saved).extracting(Message::getMessageType)
                .containsExactly(MessageType.USER, MessageType.ASSISTANT);
        assertThat(saved.getLast().getText()).isEqualTo("You were charged twice at Acme Coffee.");
    }

}

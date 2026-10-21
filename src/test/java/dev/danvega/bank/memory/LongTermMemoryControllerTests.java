package dev.danvega.bank.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import dev.danvega.bank.FakeOpenAi;
import dev.danvega.bank.FakeOpenAi.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LongTermMemoryControllerTests {

    private static final String TRIP = "I'm traveling to Portugal next week. Please text me about anything, don't email.";

    private static final String CARD_QUESTION = "Will my card work on my trip?";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    static Path memories;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FakeOpenAi.register(registry);
        registry.add("bank.memory.root", () -> memories.toString());
    }

    private final FakeOpenAi openAi = FakeOpenAi.instance();

    @Autowired
    MockMvc mvc;

    @Autowired
    ChatMemory chatMemory;

    @BeforeEach
    void reset() throws IOException {
        openAi.reset();
        try (Stream<Path> folders = Files.list(memories)) {
            for (Path folder : folders.toList()) {
                FileSystemUtils.deleteRecursively(folder);
            }
        }
    }

    @Test
    void theModelGetsSixMemoryTools() throws Exception {
        openAi.replyWithText("Hi Maria.");

        chat("C-100", "hello", "Hi").andExpect(status().isOk());

        assertThat(openAi.lastRequest().get("tools").valueStream().map(tool -> tool.get("name").asString()))
                .containsExactlyInAnyOrder("MemoryView", "MemoryCreate", "MemoryStrReplace", "MemoryInsert",
                        "MemoryDelete", "MemoryRename");
        assertThat(openAi.lastRequest().get("instructions").asString()).contains("call MemoryView on MEMORY.md");
    }

    @Test
    void theModelSavesTheTripAsMarkdownFiles() throws Exception {
        modelSavesTheTrip();

        chat("C-100", "trip", TRIP).andExpect(status().isOk());

        Path maria = memories.resolve("C-100");
        assertThat(maria.resolve("MEMORY.md")).hasContent(seed("MEMORY.md"));
        assertThat(maria.resolve("travel.md")).content().contains("type: user").contains("Portugal");
        assertThat(maria.resolve("contact-preferences.md")).content().contains("Never email");
    }

    @Test
    void aNewConversationRecallsTheTripFromTheFiles() throws Exception {
        modelSavesTheTrip();
        chat("C-100", "trip", TRIP).andExpect(status().isOk());

        openAi.reset();
        openAi.replyWithToolCalls(view("MEMORY.md"))
                .replyWithToolCalls(view("travel.md"), view("contact-preferences.md"))
                .replyWithText("Yes, your card works in Portugal. I'll text you if anything comes up.");

        chat("C-100", "card", CARD_QUESTION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(startsWith("Yes, your card works in Portugal")));

        JsonNode first = openAi.requests().getFirst();
        assertThat(userMessages(first)).containsExactly(CARD_QUESTION);
        assertThat(first.toString()).doesNotContain("Portugal");

        JsonNode last = openAi.lastRequest();
        assertThat(toolOutputs(last)).anySatisfy(output -> assertThat(output).contains("Traveling to Portugal"))
                .anySatisfy(output -> assertThat(output).contains("Never email"));
        assertThat(userMessages(last)).containsExactly(CARD_QUESTION);
    }

    @Test
    void anotherCustomerCannotSeeOrReachTheseFiles() throws Exception {
        modelSavesTheTrip();
        chat("C-100", "trip", TRIP).andExpect(status().isOk());

        String absolute = memories.resolve("C-100/MEMORY.md").toAbsolutePath().toString();
        openAi.reset();
        openAi.replyWithToolCalls(view("MEMORY.md"))
                .replyWithToolCalls(view("../C-100/MEMORY.md"), view(absolute),
                        ToolCall.of("MemoryCreate", args("path", "../C-100/MEMORY.md", "fileText", "hacked")),
                        ToolCall.of("MemoryDelete", args("path", "../C-100")))
                .replyWithText("Where are you traveling?");

        chat("C-200", "card", CARD_QUESTION).andExpect(status().isOk());

        List<String> outputs = toolOutputs(openAi.lastRequest());
        assertThat(outputs).hasSize(5);
        assertThat(outputs.getFirst()).contains("Path does not exist: MEMORY.md");
        assertThat(outputs).filteredOn(output -> output.contains("Path traversal attempt detected")).hasSize(3);
        assertThat(outputs).anySatisfy(output -> assertThat(output).contains("Absolute paths are not allowed"));
        assertThat(openAi.lastRequest().toString()).doesNotContain("Portugal").doesNotContain("Never email");

        assertThat(memories.resolve("C-100/MEMORY.md")).hasContent(seed("MEMORY.md"));
        assertThat(filesOf("C-200")).isEmpty();
    }

    @Test
    void forgetDeletesTheFolderAndTheConversations() throws Exception {
        modelSavesTheTrip();
        chat("C-100", "trip", TRIP).andExpect(status().isOk());
        openAi.replyWithText("Hi James.");
        chat("C-200", "hello", "Hi").andExpect(status().isOk());

        mvc.perform(delete("/api/memory/customers/C-100")).andExpect(status().isOk());
        assertThat(memories.resolve("C-100")).doesNotExist();
        assertThat(chatMemory.get("C-100:trip")).isEmpty();
        assertThat(chatMemory.get("C-200:hello")).hasSize(2);

        openAi.reset();
        openAi.replyWithToolCalls(view("MEMORY.md")).replyWithText("Where are you traveling?");
        chat("C-100", "trip", CARD_QUESTION).andExpect(status().isOk());
        assertThat(userMessages(openAi.lastRequest())).containsExactly(CARD_QUESTION);
        assertThat(openAi.lastRequest().toString()).doesNotContain("Portugal");
    }

    @Test
    void shortTermMemoryIsOneConversation() throws Exception {
        openAi.replyWithText("Noted.").replyWithText("You said Portugal.");

        chat("C-100", "trip", TRIP).andExpect(status().isOk());
        chat("C-100", "trip", "Where did I say I'm going?").andExpect(status().isOk());

        JsonNode second = openAi.lastRequest();
        assertThat(userMessages(second)).containsExactly(TRIP, "Where did I say I'm going?");
        assertThat(second.get("input").toString()).contains("Noted.");
    }

    @Test
    void anUnknownCustomerIsNotFound() throws Exception {
        chat("C-999", "trip", TRIP).andExpect(status().isNotFound());
        chat("../C-100", "trip", TRIP).andExpect(status().isNotFound());
        mvc.perform(delete("/api/memory/customers/C-999")).andExpect(status().isNotFound());
        assertThat(memories.resolve("C-999")).doesNotExist();
        assertThat(openAi.requests()).isEmpty();
    }

    private void modelSavesTheTrip() {
        openAi.replyWithToolCalls(view("MEMORY.md"))
                .replyWithToolCalls(create("travel.md"), create("contact-preferences.md"), create("MEMORY.md"))
                .replyWithText("Got it. Safe travels. I'll text you, never email.");
    }

    private static List<Path> filesOf(String customerId) throws IOException {
        Path folder = memories.resolve(customerId);
        if (!Files.exists(folder)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static ToolCall view(String path) {
        return ToolCall.of("MemoryView", args("path", path));
    }

    private static ToolCall create(String file) {
        return ToolCall.of("MemoryCreate", args("path", file, "fileText", seed(file)));
    }

    private static String args(String... namesAndValues) {
        ObjectNode args = JSON.createObjectNode();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            args.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return JSON.writeValueAsString(args);
    }

    private static String seed(String file) {
        try {
            return new ClassPathResource("memory-seed/C-100/" + file).getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ResultActions chat(String customerId, String conversationId, String message) throws Exception {
        return mvc.perform(post("/api/memory/customers/{id}/chat", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"conversationId":"%s","message":"%s"}""".formatted(conversationId, message)));
    }

    private static List<String> userMessages(JsonNode request) {
        return request.get("input").valueStream()
                .filter(item -> "user".equals(item.path("role").asString("")))
                .map(LongTermMemoryControllerTests::text)
                .toList();
    }

    private static List<String> toolOutputs(JsonNode request) {
        return request.get("input").valueStream()
                .filter(item -> "function_call_output".equals(item.path("type").asString("")))
                .map(item -> item.get("output").asString())
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

package dev.danvega.bank.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.+")
class MemoryLiveTests {

    @TempDir
    static Path memories;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bank.memory.root", () -> memories.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void remembersTheTripForThisCustomerOnlyUntilForgotten() throws Exception {
        chat("C-100", "trip", "I'm traveling to Portugal next week. Please text me about anything, don't email.")
                .andExpect(status().isOk());

        Path maria = memories.resolve("C-100");
        assertThat(maria.resolve("MEMORY.md")).exists();
        assertThat(contentOf(maria)).containsIgnoringCase("Portugal");

        chat("C-100", "card", "Will my card work on my trip?")
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("Portugal")));

        chat("C-200", "card", "Will my card work on my trip?")
                .andExpect(jsonPath("$.answer").value(not(containsStringIgnoringCase("Portugal"))));

        mvc.perform(delete("/api/memory/customers/C-100")).andExpect(status().isOk());
        assertThat(maria).doesNotExist();
        chat("C-100", "card", "Will my card work on my trip?")
                .andExpect(jsonPath("$.answer").value(not(containsStringIgnoringCase("Portugal"))));
    }

    private static String contentOf(Path folder) throws IOException {
        StringBuilder content = new StringBuilder();
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                content.append(Files.readString(file)).append('\n');
            }
        }
        return content.toString();
    }

    private ResultActions chat(String customerId, String conversationId, String message) throws Exception {
        return mvc.perform(post("/api/memory/customers/{id}/chat", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","message":"%s"}""".formatted(conversationId, message)))
                .andDo(print());
    }

}

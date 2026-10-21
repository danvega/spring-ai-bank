package dev.danvega.bank.router;

import java.util.Map;

import dev.danvega.bank.FakeJev;
import dev.danvega.bank.FakeOpenAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RouterControllerTests {

    private static final String FAQ = "How do I freeze my debit card?";

    private static final String DUPLICATE = "I was charged twice at Acme Coffee on October 5. Can you check?";

    private static final String HARDSHIP = "I lost my job and can't make my mortgage payment this month. What can I do?";

    @DynamicPropertySource
    static void fakes(DynamicPropertyRegistry registry) {
        FakeOpenAi.register(registry);
        FakeJev.register(registry);
    }

    private final FakeOpenAi openAi = FakeOpenAi.instance();

    private final FakeJev jev = FakeJev.instance();

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        openAi.reset();
        jev.reset();
    }

    @Test
    void aCardFaqGoesToLuna() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.92, "SOL", 0.06, "ASTRA", 0.02));
        openAi.replyWithText("Open the app, tap your card, then tap Freeze.");

        chat(FAQ).andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.tier").value("LUNA"))
                .andExpect(jsonPath("$.routing.model").value("gpt-6-luna"))
                .andExpect(jsonPath("$.routing.confidence").value(0.92))
                .andExpect(jsonPath("$.routing.gate").value("EXECUTE"))
                .andExpect(jsonPath("$.answer").value("Open the app, tap your card, then tap Freeze."));

        assertThat(modelSentToOpenAi()).isEqualTo("gpt-6-luna");
    }

    @Test
    void aDuplicateChargeGoesToSol() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.08, "SOL", 0.81, "ASTRA", 0.11));
        openAi.replyWithText("I see two charges of $18.75 at Acme Coffee on October 5.");

        chat(DUPLICATE).andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.tier").value("SOL"))
                .andExpect(jsonPath("$.routing.model").value("gpt-6.1-sol"))
                .andExpect(jsonPath("$.routing.gate").value("EXECUTE"));

        assertThat(modelSentToOpenAi()).isEqualTo("gpt-6.1-sol");
    }

    @Test
    void hardshipGoesToAstra() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.01, "SOL", 0.04, "ASTRA", 0.95));
        openAi.replyWithText("I'm sorry. We have hardship options. Here is how to start.");

        chat(HARDSHIP).andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.tier").value("ASTRA"))
                .andExpect(jsonPath("$.routing.model").value("gpt-6-astra"))
                .andExpect(jsonPath("$.routing.gate").value("EXECUTE"));

        assertThat(modelSentToOpenAi()).isEqualTo("gpt-6-astra");
    }

    @Test
    void lowConfidenceGoesToTheStrongestTier() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.22, "SOL", 0.48, "ASTRA", 0.30));
        openAi.replyWithText("Let's look at your options together.");

        chat("Can I skip a payment this month?").andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.gate").value("ESCALATE"))
                .andExpect(jsonPath("$.routing.tier").value("ASTRA"));

        assertThat(modelSentToOpenAi()).isEqualTo("gpt-6-astra");
    }

    @Test
    void lunaNeedsAClearCall() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.72, "SOL", 0.20, "ASTRA", 0.08));
        openAi.replyWithText("A late payment fee is $25.");

        chat("What's the fee if I pay my card late?").andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.gate").value("CONFIRM"))
                .andExpect(jsonPath("$.routing.tier").value("ASTRA"));

        assertThat(modelSentToOpenAi()).isEqualTo("gpt-6-astra");
    }

    @Test
    void theDecisionOnlyEndpointMakesNoModelCall() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.01, "SOL", 0.04, "ASTRA", 0.95));

        mvc.perform(post("/api/router/route").contentType(MediaType.APPLICATION_JSON).content(body(HARDSHIP)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("ASTRA"))
                .andExpect(jsonPath("$.model").value("gpt-6-astra"))
                .andExpect(jsonPath("$.confidence").value(0.95));

        assertThat(jev.requests()).hasSize(1);
        assertThat(openAi.requests()).isEmpty();
    }

    @Test
    void asksJevOneChoiceWithEveryTier() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.92, "SOL", 0.06, "ASTRA", 0.02));

        mvc.perform(post("/api/router/route").contentType(MediaType.APPLICATION_JSON).content(body(FAQ)))
                .andExpect(status().isOk());

        JsonNode request = jev.lastRequest();
        assertThat(request.get("state").asString()).isEqualTo(FAQ);
        assertThat(request.get("model").asString()).isEqualTo("jev-1.13.0");
        JsonNode tier = request.get("questions").get("tier");
        assertThat(tier.get("type").asString()).isEqualTo("choice");
        assertThat(tier.get("criteria").propertyNames()).containsExactly("LUNA", "SOL", "ASTRA");
        assertThat(tier.get("criteria").get("ASTRA").asString()).contains("Financial hardship");
        assertThat(jev.authorizations()).containsExactly("Bearer test-jev-key");
    }

    @Test
    void theModelChangesButTheOtherOptionsStay() throws Exception {
        jev.replyWith(Map.of("LUNA", 0.92, "SOL", 0.06, "ASTRA", 0.02));
        openAi.replyWithText("Open the app, tap your card, then tap Freeze.");

        chat(FAQ).andExpect(status().isOk());

        JsonNode request = openAi.lastRequest();
        assertThat(request.get("model").asString()).isEqualTo("gpt-6-luna");
        assertThat(request.get("reasoning").get("effort").asString()).isEqualTo("low");
        assertThat(request.get("instructions").asString()).contains("support assistant at a bank");
    }

    private ResultActions chat(String message) throws Exception {
        return mvc.perform(post("/api/router/chat").contentType(MediaType.APPLICATION_JSON).content(body(message)));
    }

    private static String body(String message) {
        return """
                {"message":"%s"}""".formatted(message);
    }

    private String modelSentToOpenAi() {
        assertThat(openAi.requests()).hasSize(1);
        return openAi.lastRequest().get("model").asString();
    }

}

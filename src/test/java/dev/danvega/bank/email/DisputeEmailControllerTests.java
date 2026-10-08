package dev.danvega.bank.email;

import java.nio.charset.StandardCharsets;

import dev.danvega.bank.FakeOpenAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Demo 1 without an API key. The fake plays the model, and we check both sides:
 * what Spring AI sent to the Responses API, and the typed record we got back.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisputeEmailControllerTests {

    private static final String DOUBLE_CHARGE_CASE = """
            {"cardLastFour":"4242","merchant":"Acme Coffee","amount":18.75,"transactionDate":"2026-10-05",
             "reason":"DUPLICATE_CHARGE","summary":"Charged twice for one coffee order.","missingFields":[]}
            """;

    @DynamicPropertySource
    static void openAi(DynamicPropertyRegistry registry) {
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
    void readsAnEmailIntoADisputeCase() throws Exception {
        openAi.replyWithText(DOUBLE_CHARGE_CASE);

        mvc.perform(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN).content(email("double-charge")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardLastFour").value("4242"))
                .andExpect(jsonPath("$.merchant").value("Acme Coffee"))
                .andExpect(jsonPath("$.amount").value(18.75))
                .andExpect(jsonPath("$.transactionDate").value("2026-10-05"))
                .andExpect(jsonPath("$.reason").value("DUPLICATE_CHARGE"))
                .andExpect(jsonPath("$.missingFields").isEmpty());
    }

    @Test
    void usesTheResponsesApiWithAStrictJsonSchema() throws Exception {
        openAi.replyWithText(DOUBLE_CHARGE_CASE);

        mvc.perform(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN).content(email("double-charge")))
                .andExpect(status().isOk());

        JsonNode request = openAi.lastRequest();
        assertThat(request.get("model").asString()).isEqualTo("gpt-6.1-sol");
        assertThat(request.at("/reasoning/effort").asString()).isEqualTo("low");
        assertThat(request.at("/text/format/type").asString()).isEqualTo("json_schema");
        assertThat(request.at("/text/format/strict").asBoolean()).isTrue();
        assertThat(request.at("/text/format/schema/required").valueStream().map(JsonNode::asString))
                .contains("cardLastFour", "merchant", "amount", "transactionDate", "reason", "summary",
                        "missingFields");
        assertThat(request.get("instructions").asString()).contains("dispute team");
        assertThat(request.get("input").toString()).contains("charged twice");
    }

    @Test
    void missingFieldsComeBackAsNullAndAreListed() throws Exception {
        openAi.replyWithText("""
                {"cardLastFour":null,"merchant":"Online Electronics","amount":649.00,"transactionDate":"2026-10-07",
                 "reason":"NOT_RECEIVED","summary":"Headphones never arrived.","missingFields":["cardLastFour"]}
                """);

        mvc.perform(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN)
                        .content(email("package-never-arrived")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardLastFour").isEmpty())
                .andExpect(jsonPath("$.reason").value("NOT_RECEIVED"))
                .andExpect(jsonPath("$.missingFields[0]").value("cardLastFour"));
    }

    @Test
    void validateSchemaRetriesWhenTheModelBreaksTheSchema() throws Exception {
        // First answer uses a reason code that does not exist, then the model fixes it
        openAi.replyWithText(DOUBLE_CHARGE_CASE.replace("DUPLICATE_CHARGE", "CHARGED_TWICE"))
                .replyWithText(DOUBLE_CHARGE_CASE);

        mvc.perform(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN).content(email("double-charge")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("DUPLICATE_CHARGE"));

        assertThat(openAi.requests()).hasSize(2);
        assertThat(openAi.lastRequest().get("input").toString()).contains("Output JSON validation failed");
    }

    @Test
    void sendsTheReceiptAsAnImage() throws Exception {
        openAi.replyWithText(DOUBLE_CHARGE_CASE);
        var receiptBytes = new ClassPathResource("receipts/acme-coffee-receipt.png").getContentAsByteArray();

        mvc.perform(multipart("/api/disputes/emails")
                        .file(new MockMultipartFile("email", "", "text/plain", email("see-receipt").getBytes()))
                        .file(new MockMultipartFile("receipt", "receipt.png", "image/png", receiptBytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardLastFour").value("4242"));

        String input = openAi.lastRequest().get("input").toString();
        assertThat(input).contains("\"type\":\"input_image\"").contains("data:image/png;base64,");
    }

    private static String email(String name) throws Exception {
        return new ClassPathResource("emails/" + name + ".txt").getContentAsString(StandardCharsets.UTF_8);
    }

}

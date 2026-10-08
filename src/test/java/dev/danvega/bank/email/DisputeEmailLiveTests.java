package dev.danvega.bank.email;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import dev.danvega.bank.dispute.ReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Calls the real OpenAI Responses API. Runs only when OPENAI_API_KEY is set. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class DisputeEmailLiveTests {

    @Autowired
    MockMvc mvc;

    @Test
    void readsTheDoubleChargeEmail() throws Exception {
        DisputeCase result = read(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN)
                .content(email("double-charge")));

        assertThat(result.cardLastFour()).isEqualTo("4242");
        assertThat(result.merchant()).containsIgnoringCase("Acme Coffee");
        assertThat(result.amount()).isEqualByComparingTo("18.75");
        assertThat(result.transactionDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(result.reason()).isEqualTo(ReasonCode.DUPLICATE_CHARGE);
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void listsTheMissingCardNumber() throws Exception {
        DisputeCase result = read(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN)
                .content(email("package-never-arrived")));

        assertThat(result.cardLastFour()).isNull();
        assertThat(result.missingFields()).contains("cardLastFour");
        assertThat(result.reason()).isEqualTo(ReasonCode.NOT_RECEIVED);
    }

    @Test
    void readsTheFraudEmail() throws Exception {
        DisputeCase result = read(post("/api/disputes/emails").contentType(MediaType.TEXT_PLAIN)
                .content(email("unknown-charge")));

        assertThat(result.reason()).isEqualTo(ReasonCode.FRAUD);
        assertThat(result.cardLastFour()).isEqualTo("5566");
    }

    @Test
    void fillsTheGapsFromTheReceiptPhoto() throws Exception {
        var receipt = new ClassPathResource("receipts/acme-coffee-receipt.png").getContentAsByteArray();

        DisputeCase result = read(multipart("/api/disputes/emails")
                .file(new MockMultipartFile("email", "", "text/plain", email("see-receipt").getBytes()))
                .file(new MockMultipartFile("receipt", "receipt.png", "image/png", receipt)));

        assertThat(result.cardLastFour()).isEqualTo("4242");
        assertThat(result.amount()).isEqualByComparingTo("18.75");
    }

    private DisputeCase read(RequestBuilder request) throws Exception {
        String body = mvc.perform(request).andReturn().getResponse().getContentAsString();
        System.out.println("Live answer: " + body);
        return JsonMapper.builder().build().readValue(body, DisputeCase.class);
    }

    private static String email(String name) throws Exception {
        return new ClassPathResource("emails/" + name + ".txt").getContentAsString(StandardCharsets.UTF_8);
    }

}

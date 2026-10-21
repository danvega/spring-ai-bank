package dev.danvega.bank.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.+")
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".{20,}")
class RouterLiveTests {

    @Autowired
    MockMvc mvc;

    @Test
    void jevSendsACardFaqToLuna() throws Exception {
        route("How do I freeze my debit card?").andExpect(jsonPath("$.jevPick").value("LUNA"));
    }

    @Test
    void jevSendsADuplicateChargeToSol() throws Exception {
        route("I was charged twice at Acme Coffee on October 5. Can you check?")
                .andExpect(jsonPath("$.jevPick").value("SOL"));
    }

    @Test
    void jevSendsHardshipToAstra() throws Exception {
        route("I lost my job and can't make my mortgage payment this month. What can I do?")
                .andExpect(jsonPath("$.jevPick").value("ASTRA"));
    }

    @Test
    void theChosenModelAnswers() throws Exception {
        mvc.perform(post("/api/router/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"How do I freeze my debit card?"}"""))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routing.model").exists())
                .andExpect(jsonPath("$.answer").value(not(emptyString())));
    }

    private ResultActions route(String message) throws Exception {
        return mvc.perform(post("/api/router/route").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"%s"}""".formatted(message)))
                .andDo(print())
                .andExpect(status().isOk());
    }

}

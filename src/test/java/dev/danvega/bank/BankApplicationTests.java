package dev.danvega.bank;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

/** The app starts with no OPENAI_API_KEY, so the non-LLM parts always work. */
@SpringBootTest(properties = "spring.ai.openai.api-key=not-set")
class BankApplicationTests {

    @Test
    void startsWithoutAnApiKey() {
    }

}

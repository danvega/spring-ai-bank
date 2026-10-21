package dev.danvega.bank;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.ai.openai.api-key=not-set")
class BankApplicationTests {

    @Test
    void startsWithoutAnApiKey() {
    }

}

package dev.danvega.bank.email;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

// OpenAI strict mode rejects a schema unless every property is required and no extra properties are allowed.
class DisputeCaseSchemaTests {

    private final JsonNode schema = JsonMapper.builder().build()
            .readTree(new BeanOutputConverter<>(DisputeCase.class).getJsonSchema());

    @Test
    void everyFieldIsRequiredSoStrictModeAcceptsTheSchema() {
        assertThat(schema.get("required").valueStream().map(JsonNode::asString))
                .containsExactlyInAnyOrderElementsOf(schema.get("properties").propertyNames());
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void missingValuesAreNullNotLeftOut() {
        Map.of("cardLastFour", "string", "merchant", "string", "amount", "number", "transactionDate", "string")
            .forEach((field, type) -> assertThat(schema.at("/properties/" + field + "/type").valueStream()
                .map(JsonNode::asString)).containsExactlyInAnyOrder(type, "null"));
    }

    @Test
    void reasonIsOneOfTheReasonCodes() {
        assertThat(schema.at("/properties/reason/enum").valueStream().map(JsonNode::asString))
                .containsExactly("DUPLICATE_CHARGE", "NOT_RECEIVED", "FRAUD", "WRONG_AMOUNT", "OTHER");
    }

}

# Structured output: read a dispute email

Ask for a Java type, not a string. Spring AI turns your record into a JSON schema, the model answers in JSON, and you get your record back.

This demo reads a customer's dispute email and returns a typed `DisputeCase`.

## The code

- [DisputeCase.java](DisputeCase.java): the record the model fills in. Card, merchant, amount, date, reason, a summary, and `missingFields` for anything the email doesn't say.
- [DisputeEmailController.java](DisputeEmailController.java): one call does it all.

```java
return chatClient.prompt()
        .user(email)
        .call()
        .entity(DisputeCase.class, spec -> spec
                .useProviderStructuredOutput()
                .validateSchema());
```

- `useProviderStructuredOutput()` uses OpenAI's native JSON schema mode.
- `validateSchema()` (new in 2.0) checks the answer against the schema and retries if it doesn't fit.

A second endpoint takes the email plus a receipt photo. The image fills in what the email leaves out.

## Try it

Run 2a to 2d in [http/2-dispute-email.http](../../../../../../../http/2-dispute-email.http):

- **2a** A complete email: card 4242, Acme Coffee, $18.75, `DUPLICATE_CHARGE`, nothing missing.
- **2b** No card number: `cardLastFour` is `null` and `missingFields` lists it.
- **2d** A short email plus a receipt photo: the receipt supplies the card and the amount.

## Good to know

- **OpenAI's strict mode wants every field present.** A missing value must be `null`, not left out. That's why the fields use `@Schema(nullable = true)`. Plain `@Nullable` makes a field optional, and OpenAI rejects the schema.
- **You won't see a retry in practice.** Strict mode makes a bad answer very unlikely. `DisputeEmailControllerTests` shows the retry with a fake model.
- **This works on Chat Completions too.** Unlike tool calling, structured output with GPT-6 Astra doesn't need the Responses API.

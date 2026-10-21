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
- **2b** No card number and no year: `cardLastFour` and `transactionDate` are `null`, and `missingFields` lists both.
- **2d** A short email plus a receipt photo: the receipt supplies the card and the amount.

## Why Swagger's `@Schema` is here

It's not about OpenAPI. Spring AI's schema generator reads Swagger's `@Schema` annotation, and `nullable = true` is the one way to say "this field is required, but may be `null`".

This demo needs that. With `useProviderStructuredOutput()`, OpenAI runs in strict mode, where every field must be required. But when an email doesn't give the card number, the honest answer is `null`. Here's what happens to the 2b email (no card, no year) with each choice, tested on GPT-6 Astra:

| Record | Result |
|---|---|
| Plain fields, no annotations | The model can't return `null`, so it makes something up: an empty card number and the year 9433 |
| JSpecify `@Nullable` | OpenAI rejects the schema with a 400: every field must be in `required` |
| No annotations, without `useProviderStructuredOutput()` | `validateSchema()` rejects the nulls ("null found, string expected") and retries |
| `@Schema(nullable = true)` | Both values are `null`, both are listed in `missingFields`, and validation passes the first time |

Most structured output demos skip this because their records never need `null`. A record like `Book(String title, String author)` always gets every value.

## Good to know

- **Structured output checks the shape, not the facts.** Without the "no year" line in the prompt, the model filled in a made-up year that was still a valid date.
- **You won't see a retry in practice.** Strict mode makes a bad answer very unlikely. `DisputeEmailControllerTests` shows the retry with a fake model.
- **This works on Chat Completions too.** Unlike tool calling, structured output with GPT-6 Astra doesn't need the Responses API.

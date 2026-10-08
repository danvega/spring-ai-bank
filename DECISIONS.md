# Decisions made while Dan was away

Built on October 8, 2026. No OpenAI key was available, so nothing here was checked against the real model. Everything else was run.

## Check these first

1. **Run the live tests with a key.** `OPENAI_API_KEY=... ./mvnw test -Dtest='*LiveTests'`. Nothing has touched the real model yet. They cover all three sample emails, the receipt photo, and the two-turn assistant flow.
2. **`@Schema(nullable = true)` in `DisputeCase` is load-bearing.** On the Responses API, Spring AI always sends structured output with `strict: true`. OpenAI strict mode needs every property in `required`. Plain JSpecify `@Nullable` makes Spring AI drop the field from `required`, so OpenAI should reject the schema. `@Schema(nullable = true)` keeps the field required and makes its type `["string", "null"]`. `DisputeCaseSchemaTests` guards this. It's a good stage moment, but confirm the 400 with `@Nullable` once with a key before you say it out loud.
3. **Test Claude Code against the server** on the presentation laptop. `claude mcp add --transport http bank http://localhost:8099/mcp`, then ask "Show open disputes over $500". I couldn't run it: Claude Code's login on this machine had expired.
4. **Pick the model.** The default is `gpt-6.1-sol`. Switch with `OPENAI_MODEL=gpt-6-astra`.
5. **Reasoning effort is `low`** to keep the demos fast. Both models accept `low`. Try `medium` with a key and keep it if it's fast enough.

## Versions

- **Spring AI 2.1.0-M1.** The first version with `OpenAiResponsesChatModel`. GPT-6.1 Sol and GPT-6 Astra need it for tool calling.
- **Spring Boot 4.2.0-M2.** Spring AI 2.1.0-M1's own pom sets `spring-boot.version` to 4.2.0-M2. It is also the newest Boot milestone on Maven Central today. I didn't try 4.1.x with 2.1.0-M1. Matching the build is the safe choice.
- **No milestone repository.** Both Boot 4.2.0-M2 and Spring AI 2.1.0-M1 are on Maven Central.
- **Java 21 target**, compiled and run on JDK 27. Spring AI 2.1 itself still targets 17.
- **Maven wrapper 3.9.11.**
- Pulled in by the BOM: MCP Java SDK 2.0.0, `openai-java` 4.49.0, Jackson 3.1.

## Design choices

- **One port for everything: 8099.** It matches the outline's `claude mcp add` line. The app binds to `127.0.0.1` only, because it has no auth.
- **The app starts without a key.** `spring.ai.openai.api-key=${OPENAI_API_KEY:not-set}`. MCP and the dispute endpoints work. AI calls get a 401 from OpenAI, which shows as a 500.
- **Customer ID is faked** by `CurrentCustomer` (always Maria, `C-100`). No sign-in.
- **The "existing services" return records**, not bare lists (`TransactionHistory`, `DuplicateCharges`, `DisputeList`). That keeps OpenAI and MCP clients happy, and the model gets labeled data.
- **Demo 1 is one class**, `DisputeEmailController`, with the `ChatClient` inside. Less to type live. Same for demo 2's `AssistantController`.
- **Demo 1 stretch (the receipt) is on `demo-1-done`.** If you skip it live, the done branch still has it. That's fine.
- **Demo 2 uses a per-customer tool object** (`CustomerTools`), not `@Tool` on the services. The customer ID is fixed in the object, so the model can't pass someone else's ID. There is no confirm tool at all.
- **Proposing is idempotent.** One dispute per transaction. Asking again returns the same pending dispute.
- **Confirming is a normal endpoint**, `POST /api/disputes/{id}/confirm`, outside the chat. Another customer's dispute gives a 404. A dispute that isn't pending gives a 409.
- **Dispute IDs are predictable.** Seeds are D-1000 to D-1004. The first new one after a restart is D-1005. That makes the script repeatable.
- **Tool limits:** 5 per tool, 10 in total, and `proposeDispute` at most once per turn. `on-limit-exceeded=return-error-response`, so the model hears "limit reached" instead of the request failing. The default is `THROW`.
- **Chat memory** is the auto-configured in-memory one (`MessageWindowChatMemory` on `InMemoryChatMemoryRepository`). That's the only repository that keeps reasoning across turns on Responses. The conversation ID is `customerId:conversationId`, so customers can't share memory.
- **Observations:** a small `ToolCallLogger` (an `ObservationHandler`) logs each `execute_tool` observation with its arguments. No tracing backend needed on stage. It lives in the base code, so it's there from the start.
- **I added `spring-boot-starter-actuator`** so the Micrometer observation auto-configuration is certainly on the classpath.
- **MCP tools sit on the service methods** (`@McpTool` on `TransactionService` and `DisputeService`). This is the back office view, so the tools take a customer ID. `proposeDispute` is exposed too, with `destructiveHint = false`. It still only proposes. Confirm is never exposed.
- **Read tools set only `readOnlyHint = true`.** The SDK still reports `destructiveHint: true` for them, because that's the default. The MCP spec says `destructiveHint` only matters when `readOnlyHint` is false. If the Inspector UI showing "destructive" looks bad, add `destructiveHint = false` to the three read tools.
- **No `generateOutputSchema`.** Tools return JSON text. Records are still used, so turning it on later is safe.
- **The MCP starter is added live in demo 3.** It isn't in the pom before `demo-3-done`. Build `main` once beforehand so the jar is in `~/.m2`.
- **`demo-3-start` has no `protocol` line**, so you can show the 404 if you want.
- **The receipt** is a PNG I drew with Java 2D. It's made up.
- **Tests use `FakeOpenAi`**, a small local HTTP server that speaks just enough of `/v1/responses`. That way the real `OpenAiResponsesChatModel`, `ToolCallingAdvisor`, tool limits and validation advisor all run. Mocking `ChatModel` would skip the parts most likely to break.
- **Live tests** are named `*LiveTests` and use `@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")`. With a key set, plain `./mvnw test` runs them too (and costs a few cents).
- **No auth on the MCP server.** The README says why that's only OK for a local demo, and points to `mcp-security`.

## The "To verify while building the demos" list from `outline.md`

| Item | Status | How |
|---|---|---|
| Validated structured output on Responses | **Verified at the request level. Not verified against OpenAI.** | Source: `OpenAiResponsesChatOptions` implements `StructuredOutputChatOptions`, and `ResponsesRequestBuilder` sends `text.format` as `json_schema` with `strict: true`. Tests run the real Responses model against `FakeOpenAi`: the request has the schema, and `validateSchema()` retries with the error appended ("Output JSON validation failed because of: ..."). Found the strict mode and `@Nullable` trap above. |
| Images on Responses | **Verified at the request level. Not verified against OpenAI.** | Source: `ResponsesItemMapper` turns image `Media` into an `input_image` with a data URI. A test checks the request carries `input_image` and `data:image/png;base64,...`. Whether the model reads the receipt well needs a key (`fillsTheGapsFromTheReceiptPhoto`). |
| Two providers in one app | **Verified: it does not work out of the box.** | I added `spring-ai-starter-model-anthropic` to a scratch copy. Startup fails: `chatClientBuilder` needs a single `ChatModel` and found `anthropicChatModel` and `openAiResponsesChatModel`. You need `spring.ai.chat.client.enabled=false` and a `ChatClient.builder(model)` per provider (not tried). A hand-built builder may not pick up the auto-configured tool limits. Check that before showing it. |
| MCP demo end to end | **Verified, except Claude Code.** | Ran the app. curl: `initialize` (200, session ID), `notifications/initialized` (202), `tools/list` (4 tools with hints), `tools/call findDisputes` (D-1001 and D-1002). MCP Inspector 2.10.1 CLI: `tools/list`, `tools/call`, and a missing-argument call (schema error, `isError: true`). `McpServerTests` does the same with the MCP Java SDK client. Claude Code: not run (login expired). |
| Model IDs and reasoning efforts | **Not verifiable without a key.** | Verified by source and test: the model ID is a plain string, and the request carries `"model": "gpt-6.1-sol"` and `"reasoning": {"effort": "low"}`. Whether OpenAI accepts them is untested. |

## Other things I verified by running them

- All branches compile and pass their tests. I checked each one from a fresh clone with `./mvnw -o test` (offline).
- Branches and tags share names, as asked. Git warns that the name is ambiguous. Checkout still picks the branch.
- Without a key, the app starts in about 1.4 s. `GET /api/disputes` works. Confirming another customer's dispute gives 404. A resolved one gives 409.
- Without a key, `POST /api/disputes/emails` reaches `api.openai.com` and gets `401 Incorrect API key`. So the request goes out, and the endpoint wiring is right.
- `/mcp` returns 404 when `spring.ai.mcp.server.protocol` is missing (checked on `demo-3-done` code with the line removed).
- `Registered tools: 4` shows in the log. Three harmless `BeanPostProcessorChecker` WARN lines appear, as the research said.
- The model can't call a tool that wasn't attached. A fake `confirmDispute` call fails with "No ToolCallback found for tool name: confirmDispute", and nothing is opened.
- The `proposeDispute` limit works: two calls in one turn give one dispute, and the model gets "Tool call limit (1) exceeded".
- `execute_tool <name>` lines appear in the log for each tool call.

## Risks for the live demo

- **No real model run yet.** The biggest risk. Run the live tests and the README script with a key well before October 22.
- **Milestone software.** 2.1.0-M1 on Boot 4.2.0-M2. Versions are pinned. Don't bump them on talk day (Spring release day).
- **Your `~/.m2/settings.xml` adds the Broadcom Artifactory as an always-on repository.** Every build checks it first. On venue Wi-Fi that can be slow or fail. One `spring-boot:run` here failed once on a plugin download and worked on retry. Build once at home and use `./mvnw -o` on stage.
- **Model latency.** Each assistant turn is two model calls (tool, then answer). Keep `reasoning-effort=low`.
- **Model wording varies.** The script's "expected" lines are what the data supports, not exact text. The assistant may call `recentTransactions` instead of `findDuplicateCharges`. Both are fine.
- **D-1005 assumes a fresh start.** Restart the app before demo 2, or read the ID from `awaitingConfirmation`.
- **The validation retry won't show live.** Strict mode makes a bad answer rare. Use the test to show it.
- **Claude Desktop can't reach localhost.** Use Claude Code.
- **The MCP endpoint is open.** It's bound to 127.0.0.1. Keep it that way on conference Wi-Fi.

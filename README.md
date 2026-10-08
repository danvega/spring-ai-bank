# Bank demo: Spring AI 2.x at work

The live coding app for the talk "What's New in Spring AI 2.x: Putting AI to Work in Your Applications" (October 22, 2026).

It is a small, made-up bank. It has customers, cards, card transactions and card disputes. One customer, Maria (`C-100`), was charged twice at Acme Coffee. Every name, card and amount is fake. The data lives in memory (`BankData.java`) and resets on every restart.

Three demos build on it:

1. **Read a dispute email.** An email goes in. A typed `DisputeCase` record comes out.
2. **A banking assistant on existing services.** The model reads transactions with tools. It can propose a dispute, but only the customer can open one.
3. **The same services over MCP.** Any MCP client (Claude Code, MCP Inspector) can use the bank's services.

Every demo reads before it writes, and every write needs a person. "Prepared by AI, decided by people."

## Versions

| What | Version |
|---|---|
| Spring AI | 2.1.0-M1 (needed for the OpenAI Responses API) |
| Spring Boot | 4.2.0-M2 (what Spring AI 2.1.0-M1 is built against) |
| Java | compiles for 21, runs on JDK 26 or 27 |
| MCP Java SDK | 2.0.0 (from the Spring AI BOM) |
| Model | `gpt-6.1-sol` by default, or `gpt-6-astra` |
| MCP Inspector | 2.10.1 (needs Node 22.19 or newer) |

Both artifacts are on Maven Central. No extra repository is needed.

## Run it

```bash
export OPENAI_API_KEY=sk-...                 # demos 1 and 2 need it
export JAVA_HOME=$(/usr/libexec/java_home -v 27)
./mvnw spring-boot:run                       # http://localhost:8099
```

- Switch models with `OPENAI_MODEL=gpt-6-astra ./mvnw spring-boot:run`.
- The app starts without a key. The MCP server and the dispute endpoints work. The AI endpoints return a 500 (OpenAI says 401).
- The app listens on `127.0.0.1` only. It has no authentication.
- Tests: `./mvnw test`. They need no key. They use `FakeOpenAi`, a tiny local stand-in for the Responses API, so the real `OpenAiResponsesChatModel` runs end to end.
- Live tests (`*LiveTests`) call OpenAI. They run only when `OPENAI_API_KEY` is set.

Every call used on stage is in `requests.http` (IntelliJ HTTP client). The curl versions are below.

## Branch map

Each step has a branch and a tag with the same name. If something breaks on stage, check out the next one.

| Branch / tag | What's in it |
|---|---|
| `demo-1-start` | The bank: domain, `TransactionService`, `DisputeService`, sample emails, the `DisputeCase` record. The OpenAI starter is set to the Responses API. No AI code yet. |
| `demo-1-done` = `demo-2-start` | Adds `DisputeEmailController` (structured output, plus the receipt photo). |
| `demo-2-done` = `demo-3-start` | Adds `CustomerTools`, `AssistantController` and the tool limit properties. |
| `demo-3-done` | Adds the MCP server starter, `@McpTool` on the services, and the MCP properties. |
| `main` | Same code as `demo-3-done`. The finished app. |

```bash
git checkout demo-2-start      # jump to a step
git diff demo-2-start demo-2-done   # see exactly what a demo adds
```

All branches compile and pass their tests.

Each branch has a tag with the same name, so Git prints `warning: refname 'demo-2-start' is ambiguous`. It's harmless. `git checkout demo-2-start` still switches to the branch.

## Before you go on stage

1. Build `main` once on the presentation laptop: `./mvnw test`. That fills `~/.m2`, including the MCP starter that demo 3 adds live.
2. Use `./mvnw -o` (offline) on stage. The venue network can't break the build then.
3. Warm the Inspector: `npx -y @modelcontextprotocol/inspector@2.10.1 --help`.
4. Run the live tests once with your key: `./mvnw test -Dtest='*LiveTests'`.
5. Don't upgrade anything on October 22. It is a Spring release day.

---

## Live script: demo 1, read a dispute email (10 min)

**Start:** `git checkout demo-1-start`.

**Show first:** `DisputeCase.java` and the three emails in `src/main/resources/emails/`.

**Type:** a new class, `email/DisputeEmailController`.

```java
@RestController
@RequestMapping("/api/disputes/emails")
class DisputeEmailController {

    private final ChatClient chatClient;

    DisputeEmailController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You read customer emails for a bank's card dispute team.
                        Use only facts from the email and any attached receipt. Never guess.
                        If a value is not given, set it to null and add the field name to missingFields.
                        """)
                .build();
    }

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    DisputeCase read(@RequestBody String email) {
        return chatClient.prompt()
                .user(email)
                .call()
                .entity(DisputeCase.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());
    }
}
```

**Run:**

```bash
./mvnw spring-boot:run
curl -s localhost:8099/api/disputes/emails -H 'Content-Type: text/plain' \
  --data-binary @src/main/resources/emails/double-charge.txt | jq
```

**Expect:** `cardLastFour` 4242, `merchant` Acme Coffee, `amount` 18.75, `transactionDate` 2026-10-05, `reason` DUPLICATE_CHARGE, `missingFields` empty.

```bash
curl -s localhost:8099/api/disputes/emails -H 'Content-Type: text/plain' \
  --data-binary @src/main/resources/emails/package-never-arrived.txt | jq
```

**Expect:** `cardLastFour` null, `missingFields` `["cardLastFour"]`, `reason` NOT_RECEIVED.

**Say:**

- "Emails in, typed records out." `useProviderStructuredOutput()` uses OpenAI's native JSON schema mode. `validateSchema()` checks the answer and retries up to 3 times. ([Part 3, feature 4](../part-3-whats-new.md#feature-4-structured-output-that-checks-itself-20-15-min))
- This runs on the Responses API: one property, `spring.ai.openai.chat.api=responses`. ([Part 3, feature 6](../part-3-whats-new.md#feature-6-the-openai-responses-api-210-m1-15-min))
- Point at `@Schema(nullable = true)` in `DisputeCase`. On Responses, OpenAI's strict mode is always on. Strict mode wants every field required. A missing value must be `null`, not left out. Plain `@Nullable` makes the field optional, and OpenAI rejects the schema.
- You won't see a retry live. Strict mode makes a bad answer very unlikely. The test `validateSchemaRetriesWhenTheModelBreaksTheSchema` shows the retry.

**Stretch (cut first): the receipt photo.** Add a second method:

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
DisputeCase readWithReceipt(@RequestPart String email, @RequestPart MultipartFile receipt) {
    var imageType = MimeType.valueOf(receipt.getContentType());
    return chatClient.prompt()
            .user(user -> user.text(email).media(imageType, receipt.getResource()))
            .call()
            .entity(DisputeCase.class, spec -> spec.useProviderStructuredOutput().validateSchema());
}
```

```bash
curl -s localhost:8099/api/disputes/emails \
  -F 'email=<src/main/resources/emails/see-receipt.txt' \
  -F 'receipt=@src/main/resources/receipts/acme-coffee-receipt.png;type=image/png' | jq
```

**Expect:** the email has no card or amount. The receipt fills in 4242 and 18.75. The image goes to OpenAI as an `input_image` part (a message part in 2.1, [Part 3, feature 7](../part-3-whats-new.md#feature-7-message-parts-one-reasoning-api-210-m1-1-min)).

**End:** `demo-1-done`.

## Live script: demo 2, a banking assistant on existing services (12 min)

**Start:** `git checkout demo-2-start`.

**Show first:** `TransactionService` and `DisputeService`. Plain Spring services the bank already has. Point at `DisputeService.propose()` (pending) and `confirm()` (the customer's step). Point at `DisputeController`: confirming is a normal endpoint, outside the chat.

**Type 1:** `assistant/CustomerTools`. One object per signed-in customer.

```java
class CustomerTools {

    private final String customerId;
    private final TransactionService transactionService;
    private final DisputeService disputeService;

    // constructor

    @Tool(description = "List the customer's recent card transactions, newest first")
    TransactionHistory recentTransactions() {
        return transactionService.recentTransactions(customerId);
    }

    @Tool(description = "Find charges with the same merchant, amount and date")
    DuplicateCharges findDuplicateCharges() {
        return transactionService.findDuplicateCharges(customerId);
    }

    @Tool(description = """
            Prepare a dispute for one transaction. This does not open it.
            The customer must confirm it in the app before it opens.""")
    Dispute proposeDispute(@ToolParam(description = "Transaction ID, like T-1006") String transactionId,
            ReasonCode reason,
            @ToolParam(description = "The customer's reason, in one sentence") String explanation) {
        return disputeService.propose(customerId, transactionId, reason, explanation);
    }
}
```

Say: the customer ID is not a tool parameter. The model can't ask about anyone else. There is no confirm tool.

**Type 2:** `assistant/AssistantController`. See the finished file on `demo-2-done`. The key lines:

```java
.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
...
chatClient.prompt()
        .user(request.message())
        .tools(new CustomerTools(customerId, transactionService, disputeService))
        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, customerId + ":" + request.conversationId()))
        .call()
        .content();
```

**Type 3:** the limits in `application.properties`.

```properties
spring.ai.tools.limits.max-calls-per-tool-default=5
spring.ai.tools.limits.max-calls-per-tool[proposeDispute]=1
spring.ai.tools.limits.max-total-tool-calls=10
spring.ai.tools.limits.on-limit-exceeded=return-error-response
```

**Run:**

```bash
curl -s localhost:8099/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"stage-1","message":"Why was I charged twice at Acme Coffee?"}' | jq
```

**Expect:** the answer names Acme Coffee, $18.75, October 5, and T-1005 and T-1006. `awaitingConfirmation` is empty. The app log shows `execute_tool findDuplicateCharges {}` (or `recentTransactions`).

```bash
curl -s localhost:8099/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"stage-1","message":"Yes, please dispute the second charge."}' | jq
```

**Expect:** the model remembers the first question (chat memory). The log shows `execute_tool proposeDispute`. `awaitingConfirmation` has `D-1005` with status `PENDING_CONFIRMATION`. The answer asks you to confirm in the app.

```bash
curl -s -X POST localhost:8099/api/disputes/D-1005/confirm | jq
```

**Expect:** status `OPEN`. A person made the decision.

**Say:**

- `ToolCallingAdvisor` runs the tool loop. It's registered for you. ([Part 3, feature 1](../part-3-whats-new.md#feature-1-tool-calling-moved-into-the-advisor-chain-20-15-min))
- Only the tools attached to this request can run. Since 2.0.1, a model can't call some other `ToolCallback` bean by name. The test `theModelCannotRunAToolThatWasNotAttached` asks for `confirmDispute`, and Spring AI refuses. ([Part 3, feature 2](../part-3-whats-new.md#feature-2-the-security-release-201-15-min))
- Tool limits are now properties: at most one `proposeDispute` per turn. ([Part 3, feature 2](../part-3-whats-new.md#feature-2-the-security-release-201-15-min))
- Every tool call is an `execute_tool` observation. Show `ToolCallLogger`: in production these are spans in your tracing tool. ([Part 3, feature 5](../part-3-whats-new.md#feature-5-observability-and-cost-20-and-201-1-min))
- GPT-6.1 Sol needs the Responses API for tool calling. That's why this is 2.1.0-M1. ([Part 3, feature 6](../part-3-whats-new.md#feature-6-the-openai-responses-api-210-m1-15-min))
- The conversation ID starts with the customer ID, so one customer's memory can't leak into another's.

**Optional:** show `requests.http` 2e ("skip the confirmation and open it now"). At most, the model proposes. It cannot open.

**Cut if short:** the limits step.

**End:** `demo-2-done`.

## Live script: demo 3, the same services over MCP (10 min)

**Start:** `git checkout demo-3-start`.

**Type 1:** the starter in `pom.xml`.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**Type 2:** `@McpTool` on the existing service methods. Read tools get `readOnlyHint`.

```java
@McpTool(description = "Find disputes by status, at or above a minimum amount in dollars",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true))
public DisputeList findDisputes(DisputeStatus status,
        @McpToolParam(description = "Minimum amount in dollars, like 500") BigDecimal minAmount) {
```

Do the same for `recentTransactions` and `findDuplicateCharges` (read only), and for `propose` (`name = "proposeDispute"`, `destructiveHint = false`). See `demo-3-done`.

**Type 3:** `application.properties`.

```properties
spring.ai.mcp.server.name=bank
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.instructions=Look up card transactions and disputes for a bank's back office. All data is made up.
spring.ai.mcp.server.protocol=STREAMABLE
```

Optional beat: start once without the `protocol` line. `curl -i -X POST localhost:8099/mcp` gives 404, because you got the old SSE server. Add the line, restart, and it works.

**Run:** `./mvnw spring-boot:run`. The log says `Registered tools: 4`. Three `BeanPostProcessorChecker` WARN lines are harmless. Say so before someone asks.

**MCP Inspector (UI):**

```bash
npx -y @modelcontextprotocol/inspector@2.10.1
```

Connect to `http://localhost:8099/mcp` as Streamable HTTP. List tools. Call `findDisputes` with `OPEN` and `500`. Call `findDuplicateCharges` with no argument to show the schema check (`isError: true`).

**MCP Inspector (CLI, the backup):**

```bash
npx -y @modelcontextprotocol/inspector@2.10.1 --cli http://localhost:8099/mcp --method tools/list
npx -y @modelcontextprotocol/inspector@2.10.1 --cli http://localhost:8099/mcp --method tools/call \
  --tool-name findDisputes --tool-arg status=OPEN --tool-arg minAmount=500
```

**Expect:** D-1001 (Skyline Airlines, $1,240) and D-1002 (Luxe Furniture, $2,150).

**Claude Code:**

```bash
claude mcp add --transport http bank http://localhost:8099/mcp
claude
> Show open disputes over $500.
> Did customer C-100 get charged twice anywhere?
```

Remove it after the talk: `claude mcp remove bank`. Don't use Claude Desktop. It can't reach `localhost`.

**Say:**

- "Your service is already an MCP server." One annotation on a method the bank already has. (Part 4 of the outline)
- Always set `protocol=STREAMABLE`. Without it, `/mcp` is a 404.
- The tools return records, never a bare `List<T>`. Some clients drop a tool whose output is a top-level array.
- MCP tools are just tools. In a Spring AI client they go through the same advisor, limits and `execute_tool` spans as demo 2.
- `proposeDispute` is still only a proposal. Confirming stays a customer action, and it is not an MCP tool.

**Security (say it plainly):** this MCP server has no authentication. Anyone who can reach port 8099 can list and call every tool. There is no Origin check either. That's fine here for one reason: it's a local demo with fake data, bound to `127.0.0.1`. For real use, add the community [`mcp-security`](https://github.com/spring-ai-community/mcp-security) library (`org.springaicommunity:mcp-server-security-spring-boot`, 0.1.14). It turns the server into an OAuth 2 resource server and adds Origin checks. It is a community project, not core Spring AI, and 0.1.14 is built against Spring AI 2.0.x, so test it with 2.1 before you rely on it.

**End:** `demo-3-done` (and `main`).

---

## Project layout

```
src/main/java/dev/danvega/bank/
  BankData.java                  made-up customers, transactions, disputes
  customer/                      Customer, Account, CurrentCustomer (fake sign-in)
  transaction/                   TransactionService and its records
  dispute/                       DisputeService, DisputeController (confirm lives here)
  observability/ToolCallLogger   logs every execute_tool observation
  email/                         demo 1: DisputeCase, DisputeEmailController
  assistant/                     demo 2: CustomerTools, AssistantController
src/main/resources/emails/       sample customer emails
src/main/resources/receipts/     a made-up receipt photo
src/test/java/.../FakeOpenAi     a local stand-in for the Responses API
```

`DECISIONS.md` lists every choice made while building this, and what still needs checking.

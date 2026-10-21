# Spring AI Bank

A small, made-up bank that shows Spring AI 2 at work: structured output, tool calling, short-term and long-term memory, MCP, and routing with a decision model. Each feature is one small demo on the same bank data.

Every name, card and amount is fake. One customer, Maria (`C-100`), was charged twice at Acme Coffee. The data lives in memory (`BankData.java`) and resets on every restart.

## Related content

The demo app for the talk "What's New in Spring AI 2.x: Putting AI to Work in Your Applications" (October 22, 2026).

## Tested with

Java 27 (compiles for Java 21), Spring Boot 4.2.0-M2, Spring AI 2.1.0-M1, Spring AI Agent Utils 0.13.0 and Spring AI TypeSafe 0.4.0.

## What you need

- JDK 21 or newer
- An OpenAI API key, and a TypeSafe API key for the router demo (any value works if you skip that demo):

```bash
export OPENAI_API_KEY=your-openai-key
export TYPESAFE_API_KEY=your-typesafe-key
```

- Optional: Node 22.19 or newer for MCP Inspector

## Run it

```bash
./mvnw spring-boot:run
```

The app starts on http://localhost:8099 and listens on `127.0.0.1` only.

## Try it

The [http](http) folder has one IntelliJ HTTP client file per demo, numbered in a good order to try them. Each demo folder has a short README.

| Demo | Shows | Folder |
|---|---|---|
| Read a dispute email | Structured output: an email in, a typed `DisputeCase` record out | [email](src/main/java/dev/danvega/bank/email/README.md) |
| A banking assistant | Tool calling and the tool loop on existing services. The model proposes a dispute, and the customer confirms it. | [assistant](src/main/java/dev/danvega/bank/assistant/README.md) |
| Memory | Short-term memory (chat memory, the window, the repository), then long-term memory as Markdown files | [memory](src/main/java/dev/danvega/bank/memory/README.md) |
| Your bank over MCP | `@McpTool` on the bank's services, used from any MCP client | see below |
| Route to the right model | A decision model (Jev) picks which GPT-6 model answers | [router](src/main/java/dev/danvega/bank/router/README.md) |

A first call to try:

```bash
curl -s localhost:8099/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"c1","message":"Why was I charged twice at Acme Coffee?"}'
```

The answer names two $18.75 charges at Acme Coffee on October 5. The app log shows `execute_tool findDuplicateCharges {}`.

### MCP

`TransactionService` and `DisputeService` are also an MCP server at `http://localhost:8099/mcp` (Streamable HTTP). Their methods carry `@McpTool`, and read tools set `readOnlyHint`. Connect from Claude Code:

```bash
claude mcp add --transport http bank http://localhost:8099/mcp
```

Then ask "Show open disputes over $500." Or use MCP Inspector, or the raw calls in [http/4-mcp.http](http/4-mcp.http):

```bash
npx -y @modelcontextprotocol/inspector@2.10.1
```

## Where to look

- [application.yaml](src/main/resources/application.yaml): the Responses API switch, tool limits and the MCP server settings.
- [AssistantController.java](src/main/java/dev/danvega/bank/assistant/AssistantController.java): a `ChatClient` with tools and chat memory.
- [LongTermMemoryController.java](src/main/java/dev/danvega/bank/memory/LongTermMemoryController.java): short-term and long-term memory side by side.

## Tests

```bash
./mvnw test
```

The tests need no API key. `FakeOpenAi` and `FakeJev` are small local stand-ins, so the real Spring AI clients run end to end. The `*LiveTests` call the real APIs and run only when the keys are set.

## Good to know

- **GPT-6 Astra and GPT-6.1 Sol need OpenAI's Responses API for tool calling.** `spring.ai.openai.chat.api: responses` turns it on (new in Spring AI 2.1). Comment it out and the assistant fails with a 400 from Chat Completions. The default model is `gpt-6-astra`. Change it with `OPENAI_MODEL=gpt-6.1-sol`.
- **The model can propose, never decide.** No tool confirms a dispute. Confirming is a normal endpoint, `POST /api/disputes/{id}/confirm`.
- **Long-term memory is written to `memories/`**, one folder per customer. It's git-ignored. Delete it to start over.
- **Spring AI Agent Utils and Spring AI TypeSafe are Spring AI Community projects,** not core Spring AI.
- **Demo code, not production.** There is no authentication on the app or the MCP server. The signed-in customer is hard-coded (`CurrentCustomer`). For a real MCP server, add [mcp-security](https://github.com/spring-ai-community/mcp-security).

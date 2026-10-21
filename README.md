# Spring AI Bank

A small, made-up bank that shows Spring AI 2 at work: structured output, tool calling, short-term and long-term memory, and MCP. Each feature is one small demo on the same bank data.

Every name, card and amount is fake. One customer, Maria (`C-100`), was charged twice at Acme Coffee. The data lives in memory (`BankData.java`) and resets on every restart.

## Related content

The demo app for the talk "What's New in Spring AI 2.x: Putting AI to Work in Your Applications" (October 22, 2026).

## Tested with

Java 27 (compiles for Java 21), Spring Boot 4.2.0-M2, Spring AI 2.1.0-M1 and Spring AI Agent Utils 0.13.0.

## What you need

- JDK 21 or newer
- An OpenAI API key:

```bash
export OPENAI_API_KEY=your-openai-key
```

- Optional: Node 22.19 or newer for MCP Inspector

## Run it

```bash
./mvnw spring-boot:run
```

The app starts on http://localhost:8099.

## Try it

The [http](http) folder has one IntelliJ HTTP client file per demo, numbered in a good order to try them. Each demo folder has a short README.

| Demo | Shows | Folder |
|---|---|---|
| Read a dispute email | Structured output: an email in, a typed `DisputeCase` record out | [email](src/main/java/dev/danvega/bank/email/README.md) |
| A banking assistant | Tool calling and the tool loop on existing services. The model proposes a dispute, and the customer confirms it. | [assistant](src/main/java/dev/danvega/bank/assistant/README.md) |
| Memory | Short-term memory (chat memory, the window, the repository), then long-term memory as Markdown files | [memory](src/main/java/dev/danvega/bank/memory/README.md) |
| Your bank over MCP | A small set of `@McpTool` tools designed for agents, used from any MCP client | [mcp](src/main/java/dev/danvega/bank/mcp/README.md) |

A first call to try:

```bash
curl -s localhost:8099/api/assistant/chat -H 'Content-Type: application/json' \
  -d '{"conversationId":"c1","message":"Why was I charged twice at Acme Coffee?"}'
```

The answer names two $18.75 charges at Acme Coffee on October 5. The app log shows `execute_tool findDuplicateCharges {}`.

### MCP

The app is also an MCP server at `http://localhost:8099/mcp`. The [MCP README](src/main/java/dev/danvega/bank/mcp/README.md) shows how to try it with MCP Inspector and Claude Code.

## Branches

Each branch adds one demo. `main` has them all.

| Branch | Adds |
|---|---|
| `2a-assistant` | Tool calling. It starts on Chat Completions, so tool calling fails until you turn on the Responses API. |
| `2b-structured-output` | Read a dispute email |
| `2c-memory` | Short-term and long-term memory |
| `3-mcp` | The bank's services over MCP |
| `4-jev` | Route to the right model with Jev (same as `main`) |

## Where to look

- [application.yaml](src/main/resources/application.yaml): the Responses API switch, tool limits and the MCP server settings.
- [AssistantController.java](src/main/java/dev/danvega/bank/assistant/AssistantController.java): a `ChatClient` with tools and chat memory.
- [LongTermMemoryController.java](src/main/java/dev/danvega/bank/memory/LongTermMemoryController.java): short-term and long-term memory side by side.

## Tests

```bash
./mvnw test
```

The tests need no API key. `FakeOpenAi` is a small local stand-in for OpenAI, so the real Spring AI client runs end to end. The `*LiveTests` call the real API and run only when the key is set.

## Good to know

- **GPT-6 Astra and GPT-6.1 Sol need OpenAI's Responses API for tool calling.** `spring.ai.openai.chat.api: responses` turns it on (new in Spring AI 2.1). Comment it out and the assistant fails with a 400 from Chat Completions. The model is `gpt-6-astra`.
- **The model can propose, never decide.** No tool confirms a dispute. Confirming is a normal endpoint, `POST /api/disputes/{id}/confirm`.
- **Long-term memory is written to `memories/`**, one folder per customer. It's git-ignored. Delete it to start over.
- **Spring AI Agent Utils is a Spring AI Community project,** not core Spring AI.
- **Demo code, not production.** There is no authentication on the app or the MCP server. The signed-in customer is hard-coded (`CurrentCustomer`). For a real MCP server, add [mcp-security](https://github.com/spring-ai-community/mcp-security).

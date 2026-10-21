# Spring AI Bank

A small, made-up bank that shows Spring AI 2 at work. Each demo adds one feature to the same app, on its own branch. `main` has all of them.

This is the demo app for the talk "What's New in Spring AI 2.x: Putting AI to Work in Your Applications" (October 22, 2026).

## The demos

| # | Demo | Branch | Read |
|---|---|---|---|
| 1 | Tool calling and the tool loop. Starts on Chat Completions, so tool calling fails until you turn on the Responses API. | [`01-assistant`](https://github.com/danvega/spring-ai-bank/tree/01-assistant) | [assistant](src/main/java/dev/danvega/bank/assistant/README.md) |
| 2 | Structured output: an email in, a typed record out | [`02-structured-output`](https://github.com/danvega/spring-ai-bank/tree/02-structured-output) | [email](src/main/java/dev/danvega/bank/email/README.md) |
| 3 | Short-term memory, then long-term memory | [`03-memory`](https://github.com/danvega/spring-ai-bank/tree/03-memory) | [memory](src/main/java/dev/danvega/bank/memory/README.md) |
| 4 | The bank's services as an MCP server | [`04-mcp`](https://github.com/danvega/spring-ai-bank/tree/04-mcp) | [MCP](#mcp) |
| 5 | A decision model (Jev) picks which GPT-6 model answers | `main` | [router](src/main/java/dev/danvega/bank/router/README.md) |

Each demo has an IntelliJ HTTP client file in [http](http), numbered in the same order.

## Run it

You need JDK 21 or newer, an OpenAI API key, and a TypeSafe API key for demo 5 (any value works if you skip it).

```bash
export OPENAI_API_KEY=your-openai-key
export TYPESAFE_API_KEY=your-typesafe-key
./mvnw spring-boot:run
```

The app runs on http://localhost:8099. `./mvnw test` needs no key.

### MCP

`TransactionService` and `DisputeService` carry `@McpTool`, so the app is also an MCP server at `http://localhost:8099/mcp`. Connect from Claude Code:

```bash
claude mcp add --transport http bank http://localhost:8099/mcp
```

## Resources

- [Spring AI reference docs](https://docs.spring.io/spring-ai/reference/)
- [Upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) for moving from 1.x to 2.x
- [OpenAI Responses API in Spring AI 2.1](https://docs.spring.io/spring-ai/reference/2.1/api/chat/openai-responses-chat.html)
- [Chat memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI Agent Utils](https://github.com/spring-ai-community/spring-ai-agent-utils), for `AutoMemoryTools`
- [mcp-security](https://github.com/spring-ai-community/mcp-security), to secure an MCP server

Demo code, not production: there is no authentication, and all data is fake.

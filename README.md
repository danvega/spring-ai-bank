# Spring AI Bank

A small, made-up bank that shows Spring AI 2 at work. Each demo adds one feature to the same app, on its own branch. `main` has all of them.

This is the demo app for the talk "What's New in Spring AI 2.x: Putting AI to Work in Your Applications" (October 22, 2026). The slides are in [slides/whats-new-spring-ai-2.pdf](slides/whats-new-spring-ai-2.pdf).

## The demos

| # | Demo | Branch | Read |
|---|---|---|---|
| 2a | Tool calling and the tool loop. Starts on Chat Completions, so tool calling fails until you turn on the Responses API. | [`2a-assistant`](https://github.com/danvega/spring-ai-bank/tree/2a-assistant) | [assistant](src/main/java/dev/danvega/bank/assistant/README.md) |
| 2b | Structured output: an email in, a typed record out | [`2b-structured-output`](https://github.com/danvega/spring-ai-bank/tree/2b-structured-output) | [email](src/main/java/dev/danvega/bank/email/README.md) |
| 2c | Short-term memory, then long-term memory | [`2c-memory`](https://github.com/danvega/spring-ai-bank/tree/2c-memory) | [memory](src/main/java/dev/danvega/bank/memory/README.md) |
| 3 | MCP tools designed for agents, on top of the bank's services | [`3-mcp`](https://github.com/danvega/spring-ai-bank/tree/3-mcp) | [mcp](src/main/java/dev/danvega/bank/mcp/README.md) |
| 4 | A decision model (Jev) picks which GPT-6 model answers | [`4-jev`](https://github.com/danvega/spring-ai-bank/tree/4-jev) | [router](src/main/java/dev/danvega/bank/router/README.md) |

Each demo has an IntelliJ HTTP client file in [http](http), numbered in the same order.

## Run it

You need JDK 21 or newer, an OpenAI API key, and a TypeSafe API key for demo 4 (any value works if you skip it).

```bash
export OPENAI_API_KEY=your-openai-key
export TYPESAFE_API_KEY=your-typesafe-key
./mvnw spring-boot:run
```

The app runs on http://localhost:8099. `./mvnw test` needs no key.

### MCP

The app is also an MCP server at `http://localhost:8099/mcp`. The [MCP README](src/main/java/dev/danvega/bank/mcp/README.md) shows how to try it with MCP Inspector and Claude Code.

## Resources

- [Spring AI reference docs](https://docs.spring.io/spring-ai/reference/)
- [Upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) for moving from 1.x to 2.x
- [OpenAI Responses API in Spring AI 2.1](https://docs.spring.io/spring-ai/reference/2.1/api/chat/openai-responses-chat.html)
- [Chat memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI Agent Utils](https://github.com/spring-ai-community/spring-ai-agent-utils), for `AutoMemoryTools`
- [mcp-security](https://github.com/spring-ai-community/mcp-security), to secure an MCP server

Demo code, not production: there is no authentication, and all data is fake.

# Tool calling: a banking assistant

The model can't run your code. It can only ask you to run a tool. Spring AI runs the tool, sends the result back, and calls the model again, until the model answers with text. That back and forth is the **tool loop**.

This demo puts a chat assistant on services the bank already has. The model reads transactions with tools. It can propose a dispute, but only the customer can open one.

## The code

- [CustomerTools.java](CustomerTools.java): three `@Tool` methods for one customer. `recentTransactions` and `findDuplicateCharges` only read. `proposeDispute` prepares a dispute but does not open it. The customer ID comes from our code, so the model can't ask about anyone else. There is no confirm tool.
- [AssistantController.java](AssistantController.java): attaches the tools to each request with `.tools(...)`. There's no loop in our code. Spring AI runs it.

## Tools and the loop, from scratch

A tool gives the model one of two things:

- **Context:** data it doesn't have, like `recentTransactions` and `findDuplicateCharges`.
- **An action:** a change in your app, like `proposeDispute`. For actions that matter, a person confirms.

The model never runs a tool. It asks, and your code runs it. Without a framework, you write that back and forth yourself:

```java
var reply = model.call(prompt);
while (reply.wantsTool()) {             // the model asked for a tool
    var result = run(reply.tool());     // you run it
    prompt.add(reply, result);          // you send the result back
    reply = model.call(prompt);         // and ask again
}
return reply.text();
```

The real version also needs a JSON schema for every tool, a way to map a tool name to your method, support for several tool calls in one reply, and a limit so it can't loop forever. Every framework hides this loop somewhere. In Spring AI, `@Tool` builds the schema, your method is the mapping, and `ToolCallingAdvisor` runs the loop.

## The tool loop in Spring AI 2

In 2.0, the loop moved out of each model and into the advisor chain, as `ToolCallingAdvisor`. It's registered for you.

| Before (1.x) | Now (2.0) |
|---|---|
| Each provider's `ChatModel` ran its own loop | **One loop for every provider.** Switch from OpenAI to Anthropic and tool calling behaves the same. |
| Advisors saw your question and the final answer, nothing in between | **You can see every round trip.** Each tool call is an `execute_tool` observation, and an advisor inside the loop sees every trip to the model. |
| Hard to limit or change, because it was buried in the model | **You can control it.** Only the tools attached to the request can run (2.0.1). Tool call limits are properties (2.0.1). You can turn the loop off or replace it, like the Tool Search Tool does. |
| Memory could fill up with tool chatter | **Memory stays clean.** The memory advisor sits outside the loop, so it saves only the question and the final answer. |

One catch: `ChatModel.call()` on its own no longer runs tools. Go through `ChatClient`.

## Watch the loop

Two loggers make the loop visible in the console:

- [ModelCallLogger](../observability/ModelCallLogger.java) is an advisor ordered right after `ToolCallingAdvisor`, so it runs once per trip to the model.
- [ToolCallLogger](../observability/ToolCallLogger.java) logs the `execute_tool` observation for each tool call.

One question, two trips to the model:

```
model call -> asks for findDuplicateCharges
execute_tool findDuplicateCharges {}
model call -> answers with text
```

## Try it

Run A1 to A4 in [http/2a-assistant.http](../../../../../../../http/2a-assistant.http):

1. **A1** "Why was I charged twice at Acme Coffee?" Watch the loop in the console.
2. **A2** "Yes, please dispute the second charge." The loop runs again, this time with `proposeDispute`.
3. **A3** `GET /api/disputes`: `D-1005` is `PENDING_CONFIRMATION`.
4. **A4** The customer confirms it. Now it's `OPEN`. A person made the decision.

## Good to know

- **GPT-6 Astra and GPT-6.1 Sol need the Responses API for tool calling.** Comment out `spring.ai.openai.chat.api: responses` and this demo fails with a 400 from Chat Completions.
- **Tool call limits** are in `application.yaml` under `spring.ai.tools.limits`: at most one `proposeDispute` per turn. The brackets in `"[proposeDispute]"` keep the camel-case name as the map key.
- **Restart the app for repeatable dispute IDs.** Data lives in memory, so the first new dispute is always `D-1005`.

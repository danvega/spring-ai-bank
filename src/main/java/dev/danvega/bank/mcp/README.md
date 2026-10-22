# MCP: tools designed for agents

MCP (Model Context Protocol) lets any AI agent use your application through one standard protocol: Claude Code, MCP Inspector, IDEs, or your own Spring AI client.

APIs are for systems. MCP is for LLMs. Wrapping every endpoint of an existing API as a tool gives an agent too many tools, in the wrong shape, with descriptions written for people. This demo puts a small tool layer, designed for an agent, on top of services the bank already has.

## The code

[BankMcpTools.java](BankMcpTools.java) exposes four tools for a back-office agent:

| Tool | Does | Hints |
|---|---|---|
| `findDisputes` | Disputes by status, at or above an amount | read-only |
| `recentTransactions` | A customer's recent card transactions | read-only |
| `findDuplicateCharges` | A customer's charges with the same merchant, amount and date | read-only |
| `proposeDispute` | Prepares a dispute. It stays pending until the customer confirms it | not destructive |

Each tool calls `TransactionService` or `DisputeService`. The services stay plain business code with no MCP annotations. There is no tool that confirms a dispute.

One property matters in `application.yaml`:

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE   # without it, /mcp is a 404
```

## Try it with MCP Inspector

Start the app (`./mvnw spring-boot:run`). The MCP server is at `http://localhost:8099/mcp`.

```bash
npx -y @modelcontextprotocol/inspector@2.10.1
```

It opens in your browser. Then:

1. Set the transport to **Streamable HTTP** and the URL to `http://localhost:8099/mcp`. Click **Connect**.
2. Open **Tools** and click **List Tools**. You see the four tools. The three read tools are marked read-only.
3. Run **findDisputes** with status `OPEN` and minAmount `500`. You get D-1001 (Skyline Airlines, $1,240) and D-1002 (Luxe Furniture, $2,150).
4. Run **proposeDispute** with `C-100`, `T-1006` and `DUPLICATE_CHARGE`. You get D-1005 with status `PENDING_CONFIRMATION`. Even over MCP, it only proposes.

The same calls from the command line:

```bash
npx -y @modelcontextprotocol/inspector@2.10.1 --cli http://localhost:8099/mcp --method tools/list
```

```bash
npx -y @modelcontextprotocol/inspector@2.10.1 --cli http://localhost:8099/mcp --method tools/call \
  --tool-name findDisputes --tool-arg status=OPEN --tool-arg minAmount=500
```

[http/3-mcp.http](../../../../../../../http/3-mcp.http) has the raw JSON-RPC calls (M1 to M4), if you want to see the protocol itself.

## Use it from Claude Code

1. Start the app (`./mvnw spring-boot:run`).
2. From the `spring-ai-bank` folder, add the server once:

   ```bash
   claude mcp add --transport http bank http://localhost:8099/mcp
   ```

   This uses local scope, so Claude Code only loads it when you start `claude` in this folder. You can add it before the app is running. It shows "Failed to connect" until the app is up.
3. Start `claude` in the same folder and run `/mcp`. `bank` should show as connected.

Then ask, in plain English. Claude Code asks before it calls a tool for the first time.

| Ask | What you should see |
|---|---|
| "Show open disputes over $500." | D-1001 (Skyline Airlines, $1,240) and D-1002 (Luxe Furniture, $2,150), from `findDisputes` |
| "Did customer C-100 get charged twice anywhere?" | The duplicate Acme Coffee charge, from `findDuplicateCharges` |
| "Propose a dispute for C-100's second Acme Coffee charge." | D-1005 with status `PENDING_CONFIRMATION`, from `proposeDispute` |
| "Now confirm that dispute." | Claude can't. There's no tool for it. Only the customer confirms, in the app. |

The data lives in memory, so restart the app to get D-1005 again.

Remove the server when you're done:

```bash
claude mcp remove bank -s local
```

## Good to know

- **This server has no authentication.** Anyone who can reach port 8099 can list and call every tool. For real use, add [mcp-security](https://github.com/spring-ai-community/mcp-security), a Spring AI Community project for OAuth 2 and API keys.
- **Use Claude Code, not Claude Desktop.** Claude Desktop can't reach `localhost`.
- **Tools return records, never a bare list.** Some clients drop a tool whose output is a top-level array, so `findDisputes` returns a `DisputeList`.
- **The annotations are in core Spring AI 2:** `@McpTool`, `@McpResource` and `@McpPrompt`, in `org.springframework.ai.mcp.annotation`.

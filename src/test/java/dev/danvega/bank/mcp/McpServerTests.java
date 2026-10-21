package dev.danvega.bank.mcp;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerTests {

    @LocalServerPort
    int port;

    McpSyncClient client;

    @BeforeEach
    void connect() {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port).build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    @Test
    void servesTheBankTools() {
        Map<String, Tool> tools = client.listTools().tools().stream()
                .collect(Collectors.toMap(Tool::name, Function.identity()));

        assertThat(tools).containsOnlyKeys("recentTransactions", "findDuplicateCharges", "findDisputes",
                "proposeDispute");
        assertThat(tools.get("recentTransactions").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("findDuplicateCharges").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("findDisputes").annotations().readOnlyHint()).isTrue();
        assertThat(tools.get("proposeDispute").annotations().readOnlyHint()).isFalse();
        assertThat(tools.get("proposeDispute").annotations().destructiveHint()).isFalse();
    }

    @Test
    void findsOpenDisputesOver500() {
        CallToolResult result = client.callTool(
                new CallToolRequest("findDisputes", Map.of("status", "OPEN", "minAmount", 500)));

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).contains("D-1001", "Skyline Airlines", "D-1002", "Luxe Furniture")
                .doesNotContain("D-1003", "D-1004");
    }

    @Test
    void findsTheAcmeCoffeeDoubleCharge() {
        CallToolResult result = client.callTool(
                new CallToolRequest("findDuplicateCharges", Map.of("customerId", "C-100")));

        assertThat(text(result)).contains("Acme Coffee", "T-1005", "T-1006");
    }

    @Test
    void proposingOverMcpOnlyCreatesAPendingDispute() {
        CallToolResult result = client.callTool(new CallToolRequest("proposeDispute", Map.of(
                "customerId", "C-100", "transactionId", "T-1006", "reason", "DUPLICATE_CHARGE",
                "explanation", "Charged twice for one order.")));

        assertThat(text(result)).contains("PENDING_CONFIRMATION").doesNotContain("\"OPEN\"");
    }

    @Test
    void aMissingArgumentIsAnErrorTheAgentCanSee() {
        CallToolResult result = client.callTool(new CallToolRequest("findDuplicateCharges", Map.of()));

        assertThat(result.isError()).isTrue();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

}

package dev.danvega.bank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class FakeOpenAi {

    private static final FakeOpenAi INSTANCE = new FakeOpenAi();

    private final JsonMapper json = JsonMapper.builder().build();
    private final Queue<String> replies = new ConcurrentLinkedQueue<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final HttpServer server;

    private FakeOpenAi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v1/responses", this::handle);
        server.start();
    }

    public static FakeOpenAi instance() {
        return INSTANCE;
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + INSTANCE.port() + "/v1");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.ai.openai.chat.api", () -> "responses");
        registry.add("spring.ai.openai.max-retries", () -> 0);
    }

    public void reset() {
        replies.clear();
        requests.clear();
    }

    public FakeOpenAi replyWithText(String text) {
        ObjectNode message = json.createObjectNode()
                .put("type", "message")
                .put("id", "msg_" + ids.incrementAndGet())
                .put("role", "assistant")
                .put("status", "completed");
        message.putArray("content").addObject()
                .put("type", "output_text")
                .put("text", text)
                .putArray("annotations");
        return reply(message);
    }

    public FakeOpenAi replyWithToolCalls(ToolCall... calls) {
        ObjectNode[] items = new ObjectNode[calls.length];
        for (int i = 0; i < calls.length; i++) {
            int id = ids.incrementAndGet();
            items[i] = json.createObjectNode()
                    .put("type", "function_call")
                    .put("id", "fc_" + id)
                    .put("call_id", "call_" + id)
                    .put("name", calls[i].name())
                    .put("arguments", calls[i].arguments())
                    .put("status", "completed");
        }
        return reply(items);
    }

    public List<JsonNode> requests() {
        return List.copyOf(requests);
    }

    public JsonNode lastRequest() {
        return requests.getLast();
    }

    public record ToolCall(String name, String arguments) {

        public static ToolCall of(String name, String arguments) {
            return new ToolCall(name, arguments);
        }

    }

    private FakeOpenAi reply(ObjectNode... outputItems) {
        ObjectNode response = json.createObjectNode()
                .put("id", "resp_" + ids.incrementAndGet())
                .put("object", "response")
                .put("created_at", 1791000000)
                .put("status", "completed")
                .put("model", "gpt-6.1-sol")
                .put("parallel_tool_calls", true)
                .put("tool_choice", "auto")
                .put("temperature", 1.0)
                .put("top_p", 1.0);
        response.putArray("tools");
        ArrayNode output = response.putArray("output");
        for (ObjectNode item : outputItems) {
            output.add(item);
        }
        response.putObject("usage")
                .put("input_tokens", 100)
                .put("output_tokens", 20)
                .put("total_tokens", 120);
        replies.add(json.writeValueAsString(response));
        return this;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            requests.add(json.readTree(in));
        }
        String reply = replies.poll();
        int status = reply != null ? 200 : 500;
        if (reply == null) {
            reply = "{\"error\":{\"message\":\"FakeOpenAi has no reply queued\",\"type\":\"server_error\"}}";
        }
        byte[] body = reply.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private int port() {
        return server.getAddress().getPort();
    }

}

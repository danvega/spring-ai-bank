package dev.danvega.bank;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.test.context.DynamicPropertyRegistry;

// The confidence here is just the top probability. Real Jev computes its own.
public final class FakeJev {

    private static final FakeJev INSTANCE = new FakeJev();

    private final JsonMapper json = JsonMapper.builder().build();
    private final Queue<Map<String, Double>> replies = new ConcurrentLinkedQueue<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final HttpServer server;

    private FakeJev() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v1/systemone", this::handle);
        server.start();
    }

    public static FakeJev instance() {
        return INSTANCE;
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.typesafe.base-url", () -> "http://127.0.0.1:" + INSTANCE.port());
        registry.add("spring.ai.typesafe.api-key", () -> "test-jev-key");
        registry.add("spring.ai.typesafe.retry.max-retries", () -> 0);
    }

    public void reset() {
        replies.clear();
        requests.clear();
        authorizations.clear();
    }

    public FakeJev replyWith(Map<String, Double> probabilities) {
        replies.add(probabilities);
        return this;
    }

    public List<JsonNode> requests() {
        return List.copyOf(requests);
    }

    public JsonNode lastRequest() {
        return requests.getLast();
    }

    public List<String> authorizations() {
        return List.copyOf(authorizations);
    }

    private void handle(HttpExchange exchange) throws IOException {
        JsonNode request;
        try (InputStream in = exchange.getRequestBody()) {
            request = json.readTree(in);
        }
        requests.add(request);
        authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));

        Map<String, Double> probabilities = replies.poll();
        if (probabilities == null) {
            send(exchange, 500, "{\"error\":{\"message\":\"FakeJev has no reply queued\",\"type\":\"server_error\"}}");
            return;
        }
        ObjectNode response = json.createObjectNode().put("model", request.path("model").asString("jev-latest"));
        ObjectNode answers = response.putObject("answers");
        request.get("questions").properties().forEach(question -> {
            if ("choice".equals(question.getValue().path("type").asString(""))) {
                answers.set(question.getKey(), choiceAnswer(question.getValue(), probabilities));
            }
        });
        response.putObject("usage").put("input_tokens", 150).put("output_tokens", 0);
        exchange.getResponseHeaders().add("x-typesafe-request-id", "req_" + ids.incrementAndGet());
        send(exchange, 200, json.writeValueAsString(response));
    }

    private ObjectNode choiceAnswer(JsonNode question, Map<String, Double> probabilities) {
        ObjectNode answer = json.createObjectNode().put("type", "choice");
        ObjectNode ordered = answer.putObject("probabilities");
        String best = null;
        double top = -1;
        for (String option : question.get("criteria").propertyNames()) {
            double p = probabilities.getOrDefault(option, 0.0);
            ordered.put(option, p);
            if (p > top) {
                best = option;
                top = p;
            }
        }
        return answer.put("choice", best).put("confidence", top);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private int port() {
        return server.getAddress().getPort();
    }

}

package dev.danvega.bank.router;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/router")
class RouterController {

    private final ModelRouter router;
    private final ChatClient chatClient;

    RouterController(ModelRouter router, ChatClient.Builder builder) {
        this.router = router;
        this.chatClient = builder
                .defaultSystem("""
                        You are a support assistant at a bank. Answer in plain words and keep it short.
                        Never ask for a full card number, a PIN or a password.
                        """)
                .build();
    }

    @PostMapping("/route")
    RoutingDecision route(@RequestBody CustomerQuestion question) {
        return router.route(question.message());
    }

    @PostMapping("/chat")
    RoutedAnswer chat(@RequestBody CustomerQuestion question) {
        RoutingDecision routing = router.route(question.message());
        String answer = chatClient.prompt()
                .user(question.message())
                .options(OpenAiResponsesChatOptions.builder().model(routing.model()))
                .call()
                .content();
        return new RoutedAnswer(routing, answer);
    }

    record CustomerQuestion(String message) {
    }

    record RoutedAnswer(RoutingDecision routing, String answer) {
    }

}

package dev.danvega.bank.memory;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * Step 1: short-term memory.
 * When:  the advisor
 * What:  the window (the last 10 messages, MemoryConfig)
 * Where: the repository (a Map in the JVM by default)
 */
@RestController
@RequestMapping("/api/memory/conversations/{conversationId}")
class ShortTermMemoryController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    ShortTermMemoryController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem("You are a card support assistant at a bank. Keep answers short.")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMemory = chatMemory;
    }

    @PostMapping("/chat")
    ChatReply chat(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        String answer = chatClient.prompt()
                .user(request.message())
                // Which conversation. A new ID starts empty.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return new ChatReply(answer);
    }

    @GetMapping
    List<String> history(@PathVariable String conversationId) {
        return chatMemory.get(conversationId).stream()
                .map(message -> message.getMessageType() + ": " + message.getText())
                .toList();
    }

    @DeleteMapping
    void clear(@PathVariable String conversationId) {
        chatMemory.clear(conversationId);
    }

    record ChatRequest(String message) {
    }

    record ChatReply(String answer) {
    }

}

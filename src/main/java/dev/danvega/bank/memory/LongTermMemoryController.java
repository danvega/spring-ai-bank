package dev.danvega.bank.memory;

import java.io.IOException;
import java.nio.file.Path;

import dev.danvega.bank.BankData;
import org.springaicommunity.agent.tools.AutoMemoryTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Step 2: long-term memory. Markdown files per customer, written by the model with AutoMemoryTools.
@RestController
@RequestMapping("/api/memory/customers/{customerId}")
class LongTermMemoryController {

    private static final String SYSTEM = """
            You are a card support assistant at a bank. Keep answers short.

            You have long-term memory about this customer: Markdown files you manage with the Memory tools.
            MEMORY.md is the index.

            Before you answer, call MemoryView on MEMORY.md, then view any file it lists that could help.
            If MEMORY.md does not exist, you have no memories of this customer yet.

            When the customer tells you something that will matter later, like a trip or how to contact them:
            - Save it in a file, one topic per file, like travel.md. Update the file if it exists.
            - Add one line for the file to MEMORY.md: "- [Title](file.md) - one-line summary".
            - Never save card numbers, PINs, passwords, account numbers or one-time codes.
            """;

    private final ChatClient chatClient;
    private final ChatMemoryRepository conversations;
    private final Path root;

    LongTermMemoryController(ChatClient.Builder builder, ChatMemory chatMemory, ChatMemoryRepository conversations,
            @Value("${bank.memory.root}") Path root) {
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.conversations = conversations;
        this.root = root.toAbsolutePath().normalize();
    }

    @PostMapping("/chat")
    ChatReply chat(@PathVariable String customerId, @RequestBody ChatRequest request) {
        requireCustomer(customerId);
        String answer = chatClient.prompt()
                .user(request.message())
                // Short-term: this chat
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, customerId + ":" + request.conversationId()))
                // Long-term: this customer's folder
                .tools(AutoMemoryTools.builder().memoriesDir(root.resolve(customerId)).build())
                .call()
                .content();
        return new ChatReply(answer);
    }

    // Deletes the folder and every chat
    @DeleteMapping
    void forget(@PathVariable String customerId) throws IOException {
        requireCustomer(customerId);
        FileSystemUtils.deleteRecursively(root.resolve(customerId));
        conversations.findConversationIds().stream()
                .filter(id -> id.startsWith(customerId + ":"))
                .forEach(conversations::deleteByConversationId);
    }

    // Known IDs only, so "../C-100" can't become a folder
    private static void requireCustomer(String customerId) {
        if (BankData.customers().stream().noneMatch(customer -> customer.id().equals(customerId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No customer " + customerId);
        }
    }

    record ChatRequest(String conversationId, String message) {
    }

    record ChatReply(String answer) {
    }

}

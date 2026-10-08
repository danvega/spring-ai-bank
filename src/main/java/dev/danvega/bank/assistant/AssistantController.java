package dev.danvega.bank.assistant;

import java.util.List;

import dev.danvega.bank.customer.CurrentCustomer;
import dev.danvega.bank.dispute.Dispute;
import dev.danvega.bank.dispute.DisputeService;
import dev.danvega.bank.transaction.TransactionService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private final ChatClient chatClient;
    private final TransactionService transactionService;
    private final DisputeService disputeService;
    private final CurrentCustomer currentCustomer;

    AssistantController(ChatClient.Builder builder, ChatMemory chatMemory, TransactionService transactionService,
            DisputeService disputeService, CurrentCustomer currentCustomer) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a card support assistant at a bank. You help one signed-in customer.
                        Use the tools to look at their transactions. Keep answers short.
                        You can propose a dispute, but you can never open one.
                        After you propose one, give its ID and ask the customer to confirm it in the app.
                        """)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.transactionService = transactionService;
        this.disputeService = disputeService;
        this.currentCustomer = currentCustomer;
    }

    @PostMapping("/chat")
    ChatReply chat(@RequestBody ChatRequest request) {
        String customerId = currentCustomer.id();
        var tools = new CustomerTools(customerId, transactionService, disputeService);

        String answer = chatClient.prompt()
                .user(request.message())
                .tools(tools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, customerId + ":" + request.conversationId()))
                .call()
                .content();

        return new ChatReply(answer, disputeService.awaitingConfirmation(customerId));
    }

    record ChatRequest(String conversationId, String message) {
    }

    record ChatReply(String answer, List<Dispute> awaitingConfirmation) {
    }

}

package dev.danvega.bank.observability;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;

// Runs inside the tool loop: once per trip to the model.
public class ModelCallLogger implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ModelCallLogger.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        AssistantMessage reply = response.chatResponse().getResult().getOutput();
        if (reply.hasToolCalls()) {
            String tools = reply.getToolCalls().stream()
                    .map(AssistantMessage.ToolCall::name)
                    .collect(Collectors.joining(", "));
            log.info("model call -> asks for {}", tools);
        }
        else {
            log.info("model call -> answers with text");
        }
        return response;
    }

    @Override
    public String getName() {
        return "ModelCallLogger";
    }

    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 1;
    }

}

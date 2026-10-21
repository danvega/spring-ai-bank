package dev.danvega.bank.email;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/disputes/emails")
class DisputeEmailController {

    private final ChatClient chatClient;

    DisputeEmailController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You read customer emails for a bank's card dispute team.
                        Use only facts from the email and any attached receipt. Never guess.
                        If a value is not given, set it to null and add the field name to missingFields.
                        If the date has no year, set transactionDate to null too.
                        """)
                .build();
    }

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    DisputeCase read(@RequestBody String email) {
        return chatClient.prompt()
                .user(email)
                .call()
                .entity(DisputeCase.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DisputeCase readWithReceipt(@RequestPart String email, @RequestPart MultipartFile receipt) {
        var imageType = MimeType.valueOf(receipt.getContentType());
        return chatClient.prompt()
                .user(user -> user.text(email).media(imageType, receipt.getResource()))
                .call()
                .entity(DisputeCase.class, spec -> spec
                        .useProviderStructuredOutput()
                        .validateSchema());
    }

}

package com.signet.send;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signet.shared.config.Mailbox;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SendWorkflowTest {

    @Mock
    private SendTransactions tx;
    @Mock
    private ReplyBuilder replyBuilder;
    @Mock
    private MailSenderFactory senderFactory;
    @Mock
    private JavaMailSender sender;
    @Mock
    private MimeMessage message;

    @InjectMocks
    private SendWorkflow workflow;

    private final UUID emailId = UUID.randomUUID();
    private SendPayload payload;

    @BeforeEach
    void setUp() {
        Mailbox mailbox = new Mailbox();
        mailbox.setId("personal");
        mailbox.setUsername("alex@gmail.com");
        mailbox.setSmtpHost("smtp.gmail.com");
        payload = new SendPayload(emailId, UUID.randomUUID(), "<msg-1@x>",
                "ivan@example.com", "Re: привет", "Текст ответа", mailbox);
    }

    @Test
    void не_отправляет_если_письмо_уже_занято_или_отправлено() {
        when(tx.claim(emailId)).thenReturn(Optional.empty());

        workflow.deliver(emailId);

        // Ключевая защита от дублей: без успешного claim SMTP не трогаем вообще.
        verifyNoInteractions(senderFactory, replyBuilder);
        verify(tx, never()).markSent(any(), anyString());
    }

    @Test
    void отправляет_и_фиксирует_успех() throws Exception {
        when(tx.claim(emailId)).thenReturn(Optional.of(payload));
        when(senderFactory.forMailbox(payload.mailbox())).thenReturn(sender);
        when(replyBuilder.build(sender, payload, "alex@gmail.com")).thenReturn(message);
        when(message.getMessageID()).thenReturn("<sent-1@gmail.com>");

        workflow.deliver(emailId);

        verify(sender).send(message);
        verify(tx).markSent(payload, "<sent-1@gmail.com>");
        verify(tx, never()).markFailed(any(), anyString());
    }

    @Test
    void при_ошибке_smtp_фиксирует_неудачу_и_не_помечает_отправленным() throws Exception {
        when(tx.claim(emailId)).thenReturn(Optional.of(payload));
        when(senderFactory.forMailbox(payload.mailbox())).thenReturn(sender);
        when(replyBuilder.build(sender, payload, "alex@gmail.com")).thenReturn(message);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp timeout"))
                .when(sender).send(message);

        workflow.deliver(emailId);

        verify(tx, never()).markSent(any(), anyString());
        verify(tx).markFailed(eq(emailId), anyString());
    }
}

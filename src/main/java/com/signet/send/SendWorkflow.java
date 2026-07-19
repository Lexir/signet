package com.signet.send;

import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сценарий отправки. Отдельный бин от слушателя — иначе {@code NOT_SUPPORTED}
 * не сработал бы при вызове изнутри одного класса (self-invocation минует прокси).
 *
 * <p>NOT_SUPPORTED приостанавливает транзакцию слушателя и возвращает соединение
 * в пул: на время SMTP-обмена БД не занята.
 */
@Service
public class SendWorkflow {

    private static final Logger log = LoggerFactory.getLogger(SendWorkflow.class);

    private final SendTransactions tx;
    private final ReplyBuilder replyBuilder;
    private final MailSenderFactory senderFactory;

    public SendWorkflow(SendTransactions tx, ReplyBuilder replyBuilder, MailSenderFactory senderFactory) {
        this.tx = tx;
        this.replyBuilder = replyBuilder;
        this.senderFactory = senderFactory;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deliver(UUID emailId) {
        Optional<SendPayload> claimed = tx.claim(emailId);
        if (claimed.isEmpty()) {
            return;                       // уже отправлено, занято или нечего слать
        }
        SendPayload payload = claimed.get();
        try {
            JavaMailSender sender = senderFactory.forMailbox(payload.mailbox());
            MimeMessage message = replyBuilder.build(sender, payload, payload.mailbox().getUsername());
            sender.send(message);                                   // вне транзакции
            tx.markSent(payload, message.getMessageID());
            log.info("[{}] Ответ отправлен по письму {}", payload.mailbox().getId(), emailId);
        } catch (Exception ex) {
            log.error("Ошибка отправки письма {}: {}", emailId, ex.getMessage(), ex);
            tx.markFailed(emailId, ex.getMessage());
        }
    }
}

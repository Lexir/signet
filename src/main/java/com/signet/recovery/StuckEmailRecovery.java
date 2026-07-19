package com.signet.recovery;

import com.signet.review.ReviewProperties;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.domain.ReviewStatus;
import com.signet.shared.domain.ReviewTask;
import com.signet.shared.event.Events;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.ReviewTaskRepository;
import com.signet.shared.repo.SendLogRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Разбирает письма, застрявшие в промежуточных статусах после падения процесса,
 * и напоминает о задачах ревью, по которым долго нет решения.
 *
 * <p>Главный принцип: письмо из статуса SENDING <b>никогда не переотправляется
 * автоматически</b>. Мы не знаем, приняла ли его почта до падения, а дубликат
 * для получателя хуже задержки — поэтому решение остаётся за человеком.
 */
@Component
public class StuckEmailRecovery {

    private static final Logger log = LoggerFactory.getLogger(StuckEmailRecovery.class);

    private final EmailRepository emails;
    private final SendLogRepository sendLog;
    private final ReviewTaskRepository reviews;
    private final RecoveryProperties props;
    private final ReviewProperties reviewProps;
    private final ApplicationEventPublisher events;

    public StuckEmailRecovery(EmailRepository emails,
                              SendLogRepository sendLog,
                              ReviewTaskRepository reviews,
                              RecoveryProperties props,
                              ReviewProperties reviewProps,
                              ApplicationEventPublisher events) {
        this.emails = emails;
        this.sendLog = sendLog;
        this.reviews = reviews;
        this.props = props;
        this.reviewProps = reviewProps;
        this.events = events;
    }

    @Scheduled(fixedDelayString = "${app.recovery.scan-interval:5m}")
    @Transactional
    public void scan() {
        if (!props.isEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(props.getStuckAfter());
        recoverDrafting(cutoff);
        resolveSending(cutoff);
        remindAboutPendingReview();
    }

    /**
     * Генерация не дошла до конца — черновика нет, поэтому безопасно вернуть
     * письмо в очередь и попробовать снова.
     */
    private void recoverDrafting(Instant cutoff) {
        for (Email email : emails.findByStatusAndUpdatedAtBefore(EmailStatus.DRAFTING, cutoff)) {
            email.setStatus(EmailStatus.RECEIVED);
            emails.save(email);
            events.publishEvent(new Events.EmailReceived(email.getId()));
            log.warn("Письмо {} зависло на генерации — возвращено в очередь", email.getId());
        }
    }

    /**
     * Отправка прервалась на полпути. Автоматически ничего не переотправляем:
     * если факт отправки зафиксирован — просто чиним статус, иначе зовём человека.
     */
    private void resolveSending(Instant cutoff) {
        for (Email email : emails.findByStatusAndUpdatedAtBefore(EmailStatus.SENDING, cutoff)) {
            if (sendLog.existsByEmailIdAndStatus(email.getId(), "SENT")) {
                email.setStatus(EmailStatus.SENT);
                emails.save(email);
                log.warn("Письмо {} было отправлено, но статус не обновился — исправлено", email.getId());
                continue;
            }
            email.setStatus(EmailStatus.FAILED);
            emails.save(email);
            events.publishEvent(new Events.ProcessingFailed(email.getId(), "send",
                    "отправка прервалась; письмо МОГЛО уйти — проверьте папку «Отправленные» "
                            + "в почте перед повторной отправкой"));
            log.error("Письмо {} зависло в SENDING — требуется ручная проверка", email.getId());
        }
    }

    /** Черновик ждёт решения дольше таймаута — напоминаем один раз. */
    private void remindAboutPendingReview() {
        Duration timeout = reviewProps.getTimeout();
        Instant cutoff = Instant.now().minus(timeout);

        for (ReviewTask task : reviews.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(
                ReviewStatus.PENDING, cutoff)) {
            long waiting = Duration.between(task.getCreatedAt(), Instant.now()).toMinutes();
            task.setRemindedAt(Instant.now());
            reviews.save(task);
            events.publishEvent(new Events.ReviewReminder(task.getEmailId(), waiting));
            log.info("Напоминание о задаче ревью для письма {} (ждёт {} мин)", task.getEmailId(), waiting);
        }
    }
}

package com.signet.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signet.review.ReviewProperties;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.domain.ReviewStatus;
import com.signet.shared.domain.ReviewTask;
import com.signet.shared.event.Events;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.ReviewTaskRepository;
import com.signet.shared.repo.SendLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StuckEmailRecoveryTest {

    @Mock
    private EmailRepository emails;
    @Mock
    private SendLogRepository sendLog;
    @Mock
    private ReviewTaskRepository reviews;
    @Mock
    private ApplicationEventPublisher events;

    private StuckEmailRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new StuckEmailRecovery(emails, sendLog, reviews,
                new RecoveryProperties(), new ReviewProperties(), events);

        when(emails.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());
        when(reviews.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());
    }

    private Email stuck(EmailStatus status) {
        Email email = new Email();
        email.setMessageId("<stuck@x>");
        email.setFromAddr("ivan@example.com");
        email.setStatus(status);
        return email;
    }

    @Test
    void зависшая_генерация_возвращается_в_очередь() {
        Email email = stuck(EmailStatus.DRAFTING);
        when(emails.findByStatusAndUpdatedAtBefore(eqStatus(EmailStatus.DRAFTING), any()))
                .thenReturn(List.of(email));

        recovery.scan();

        assertThat(email.getStatus()).isEqualTo(EmailStatus.RECEIVED);
        // Переигрываем форсированной генерацией (ReplyRequested), а не авто-путём EmailReceived —
        // иначе классификатор мог бы отбросить ответ на «не личного» отправителя.
        verify(events).publishEvent(any(Events.ReplyRequested.class));
    }

    @Test
    void зависшее_в_sending_с_подтверждённой_отправкой_чинит_статус() {
        Email email = stuck(EmailStatus.SENDING);
        when(emails.findByStatusAndUpdatedAtBefore(eqStatus(EmailStatus.SENDING), any()))
                .thenReturn(List.of(email));
        when(sendLog.existsByEmailIdAndStatus(email.getId(), "SENT")).thenReturn(true);

        recovery.scan();

        assertThat(email.getStatus()).isEqualTo(EmailStatus.SENT);
        verify(events, never()).publishEvent(any(Events.ProcessingFailed.class));
    }

    @Test
    void зависшее_в_sending_без_подтверждения_НЕ_переотправляется_а_зовёт_человека() {
        Email email = stuck(EmailStatus.SENDING);
        when(emails.findByStatusAndUpdatedAtBefore(eqStatus(EmailStatus.SENDING), any()))
                .thenReturn(List.of(email));
        when(sendLog.existsByEmailIdAndStatus(email.getId(), "SENT")).thenReturn(false);

        recovery.scan();

        // Ключевое: никакого автоматического повтора — дубликат хуже задержки.
        assertThat(email.getStatus()).isEqualTo(EmailStatus.FAILED);
        verify(events, never()).publishEvent(any(Events.ReviewApproved.class));

        ArgumentCaptor<Events.ProcessingFailed> captor =
                ArgumentCaptor.forClass(Events.ProcessingFailed.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().reason()).contains("Отправленные");
    }

    @Test
    void напоминает_о_задаче_ревью_только_один_раз() {
        ReviewTask task = new ReviewTask(UUID.randomUUID(), UUID.randomUUID(),
                com.signet.shared.domain.ReviewChannel.TELEGRAM);
        when(reviews.findByStatusAndRemindedAtIsNullAndCreatedAtBefore(
                eqReviewStatus(ReviewStatus.PENDING), any())).thenReturn(List.of(task));

        recovery.scan();

        assertThat(task.getRemindedAt()).isNotNull();   // повторно уже не попадёт в выборку
        verify(events).publishEvent(any(Events.ReviewReminder.class));
    }

    @Test
    void при_выключенном_recovery_ничего_не_делает() {
        RecoveryProperties disabled = new RecoveryProperties();
        disabled.setEnabled(false);
        StuckEmailRecovery off = new StuckEmailRecovery(emails, sendLog, reviews,
                disabled, new ReviewProperties(), events);

        off.scan();

        verify(emails, never()).findByStatusAndUpdatedAtBefore(any(), any());
    }

    private static EmailStatus eqStatus(EmailStatus status) {
        return org.mockito.ArgumentMatchers.eq(status);
    }

    private static ReviewStatus eqReviewStatus(ReviewStatus status) {
        return org.mockito.ArgumentMatchers.eq(status);
    }
}

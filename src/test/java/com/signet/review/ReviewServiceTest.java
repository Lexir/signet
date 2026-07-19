package com.signet.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signet.ai.AiProperties;
import com.signet.ai.TranslationService;
import com.signet.shared.domain.Draft;
import com.signet.shared.domain.Email;
import com.signet.shared.domain.EmailStatus;
import com.signet.shared.domain.ReviewChannel;
import com.signet.shared.domain.ReviewStatus;
import com.signet.shared.domain.ReviewTask;
import com.signet.shared.event.Events;
import com.signet.shared.repo.DraftRepository;
import com.signet.shared.repo.EmailRepository;
import com.signet.shared.repo.ReviewTaskRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock
    private EmailRepository emails;
    @Mock
    private DraftRepository drafts;
    @Mock
    private ReviewTaskRepository reviews;
    @Mock
    private ReviewTransactions tx;
    @Mock
    private TelegramGateway telegram;
    @Mock
    private TranslationService translation;
    @Mock
    private ApplicationEventPublisher events;

    private ReviewService service;

    private Email email;
    private Draft draft;
    private ReviewTask task;

    @BeforeEach
    void setUp() {
        AiProperties aiProps = new AiProperties();          // managerLanguage = "ru"
        service = new ReviewService(emails, drafts, reviews, tx, telegram, translation, aiProps, events);

        email = new Email();
        email.setMessageId("<msg-1@x>");
        email.setFromAddr("ivan@example.com");
        email.setLanguage("en");
        email.setStatus(EmailStatus.PENDING_REVIEW);

        draft = new Draft(email.getId());
        draft.setAiText("Original draft");

        task = new ReviewTask(email.getId(), draft.getId(), ReviewChannel.TELEGRAM);

        when(emails.findById(email.getId())).thenReturn(Optional.of(email));
        when(drafts.findById(draft.getId())).thenReturn(Optional.of(draft));
    }

    @Test
    void без_активной_правки_текст_игнорируется() {
        when(reviews.findFirstByAwaitingEditTrueOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThat(service.applyEditText("новый текст", "alex")).isFalse();

        verify(drafts, never()).save(any());
    }

    @Test
    void правка_переводится_на_язык_собеседника_и_становится_финальной() {
        task.setAwaitingEdit(true);
        when(reviews.findFirstByAwaitingEditTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(task));
        when(translation.translate("Поправленный ответ", "en")).thenReturn("Corrected reply");

        boolean applied = service.applyEditText("Поправленный ответ", "alex");

        assertThat(applied).isTrue();
        // Собеседнику уходит текст на ЕГО языке, а не русская правка.
        assertThat(draft.getFinalText()).isEqualTo("Corrected reply");
        assertThat(task.getStatus()).isEqualTo(ReviewStatus.EDITED);
        assertThat(task.isAwaitingEdit()).isFalse();
        assertThat(email.getStatus()).isEqualTo(EmailStatus.EDITED);
        verify(events).publishEvent(any(Events.ReviewApproved.class));
    }

    @Test
    void если_язык_совпадает_перевод_не_вызывается() {
        email.setLanguage("ru");
        task.setAwaitingEdit(true);
        when(reviews.findFirstByAwaitingEditTrueOrderByCreatedAtDesc()).thenReturn(Optional.of(task));

        service.applyEditText("Поправленный ответ", "alex");

        verify(translation, never()).translate(anyString(), anyString());
        assertThat(draft.getFinalText()).isEqualTo("Поправленный ответ");
    }

    @Test
    void при_запросе_правки_снимается_ожидание_с_других_писем() {
        when(reviews.findByEmailId(email.getId())).thenReturn(Optional.of(task));

        service.requestEdit(email.getId());

        // Иначе следующий присланный текст мог бы уйти в другое письмо.
        verify(reviews).clearAwaitingEditExcept(task.getId());
        assertThat(task.isAwaitingEdit()).isTrue();
    }

    @Test
    void одобрение_копирует_черновик_в_финальный_текст() {
        when(reviews.findByEmailId(email.getId())).thenReturn(Optional.of(task));

        service.approve(email.getId(), "alex");

        assertThat(draft.getFinalText()).isEqualTo("Original draft");
        assertThat(task.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(email.getStatus()).isEqualTo(EmailStatus.APPROVED);
        verify(events).publishEvent(any(Events.ReviewApproved.class));
    }

    @Test
    void повторное_решение_по_закрытой_задаче_игнорируется() {
        task.setStatus(ReviewStatus.APPROVED);
        when(reviews.findByEmailId(email.getId())).thenReturn(Optional.of(task));

        service.approve(email.getId(), "alex");
        service.reject(email.getId(), "alex");

        verify(events, never()).publishEvent(any(Events.ReviewApproved.class));
        verify(events, never()).publishEvent(any(Events.ReviewRejected.class));
    }
}

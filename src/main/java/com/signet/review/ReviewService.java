package com.signet.review;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Валидация человеком через Telegram: отправка черновика (с переводом на русский),
 * обработка решений approve / edit / reject.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final int MAX_BLOCK = 1200;

    private final EmailRepository emails;
    private final DraftRepository drafts;
    private final ReviewTaskRepository reviews;
    private final ReviewTransactions tx;
    private final TelegramGateway telegram;
    private final TranslationService translation;
    private final AiProperties aiProps;
    private final ApplicationEventPublisher events;

    public ReviewService(EmailRepository emails,
                         DraftRepository drafts,
                         ReviewTaskRepository reviews,
                         ReviewTransactions tx,
                         TelegramGateway telegram,
                         TranslationService translation,
                         AiProperties aiProps,
                         ApplicationEventPublisher events) {
        this.emails = emails;
        this.drafts = drafts;
        this.reviews = reviews;
        this.tx = tx;
        this.telegram = telegram;
        this.translation = translation;
        this.aiProps = aiProps;
        this.events = events;
    }

    /**
     * Открывает задачу ревью. Перевод (LLM) и работа с Telegram, включая заливку
     * вложений, идут ВНЕ транзакции — иначе соединение с БД удерживается всё это время.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void openReview(UUID emailId, UUID draftId) {
        // Событие DraftReady может прийти повторно (перезапуск, ретрай реестра публикаций).
        // Без этой проверки создалась бы вторая ReviewTask на письмо — и findByEmailId,
        // ожидающий один результат, начал бы падать. Заодно не дублируем сообщение в Telegram.
        if (tx.reviewAlreadyOpen(emailId)) {
            log.info("Ревью для письма {} уже открыто — повторная доставка пропущена", emailId);
            return;
        }

        // Канал разбора выбирается на уровне ящика: веб-очередь (UI) или Telegram-бот.
        ReviewChannel channel = tx.channelFor(emailId);
        if (channel == ReviewChannel.UI) {
            tx.persistReviewTask(emailId, draftId, ReviewChannel.UI, null);
            log.info("Ревью письма {} поставлено в UI-очередь", emailId);
            return;
        }

        ReviewPayload payload = tx.loadForReview(emailId, draftId);

        String managerLang = aiProps.getManagerLanguage();
        boolean needTranslation = payload.language() != null
                && !managerLang.equalsIgnoreCase(payload.language());

        String clientRu = needTranslation
                ? translation.translate(payload.body(), managerLang)   // вне транзакции
                : null;

        Integer messageId = telegram.sendReview(
                buildMessage(payload, clientRu, needTranslation), emailId);

        // Вложения читаем и отправляем по одному, чтобы не держать в heap все файлы письма.
        for (UUID attachmentId : tx.attachmentIds(emailId)) {
            tx.loadAttachment(attachmentId).ifPresent(att ->
                    telegram.sendDocument(att.getData(), att.getFilename(), "📎 " + att.getFilename()));
        }

        tx.persistReviewTask(emailId, draftId, ReviewChannel.TELEGRAM, messageId);
        log.info("Ревью отправлено для письма {}", emailId);
    }

    /** Очередь UI-ревью: ожидающие решения задачи с каналом UI (для веб-интерфейса). */
    @Transactional(readOnly = true)
    public List<ReviewQueueItem> pendingUiQueue() {
        return reviews.findByStatusAndChannelOrderByCreatedAtAsc(ReviewStatus.PENDING, ReviewChannel.UI).stream()
                .map(task -> {
                    ReviewPayload p = tx.loadForReview(task.getEmailId(), task.getDraftId());
                    return new ReviewQueueItem(p.emailId(), p.mailboxLabel(), p.from(), p.subject(),
                            p.language(), p.body(), p.draftText(), p.draftTextRu(), task.getCreatedAt());
                })
                .toList();
    }

    /** Элемент очереди UI-ревью (письмо + черновик) для веб-интерфейса. */
    public record ReviewQueueItem(UUID emailId, String mailboxLabel, String from, String subject,
                                  String language, String clientBody, String aiText, String aiTextRu,
                                  Instant createdAt) {
    }

    /** Человек одобрил: финальный текст = черновик как есть. */
    @Transactional
    public void approve(UUID emailId, String reviewer) {
        pendingReview(emailId).ifPresent(task -> {
            Draft draft = drafts.findById(task.getDraftId()).orElseThrow();
            draft.setFinalText(draft.getAiText());
            drafts.save(draft);

            finishReview(task, ReviewStatus.APPROVED, reviewer);
            transition(emailId, EmailStatus.APPROVED);
            events.publishEvent(new Events.ReviewApproved(emailId));
            notifyTelegram(task, "✅ Отправляю ответ.");
        });
    }

    /** Человек отклонил: ничего не отправляем. */
    @Transactional
    public void reject(UUID emailId, String reviewer) {
        pendingReview(emailId).ifPresent(task -> {
            finishReview(task, ReviewStatus.REJECTED, reviewer);
            transition(emailId, EmailStatus.REJECTED);
            events.publishEvent(new Events.ReviewRejected(emailId, "rejected by user"));
            notifyTelegram(task, "❌ Ответ отклонён, ничего не отправлено.");
        });
    }

    /** Обратная связь в Telegram — только для задач телеграм-канала (UI отвечает по HTTP). */
    private void notifyTelegram(ReviewTask task, String text) {
        if (task.getChannel() == ReviewChannel.TELEGRAM) {
            telegram.sendText(text);
        }
    }

    /**
     * Нажата «Редактировать». Снимаем ожидание правки с других писем, чтобы
     * следующий текст гарантированно ушёл именно в это письмо.
     */
    @Transactional
    public void requestEdit(UUID emailId) {
        pendingReview(emailId).ifPresent(task -> {
            reviews.clearAwaitingEditExcept(task.getId());
            task.setAwaitingEdit(true);
            reviews.save(task);
            telegram.sendText("✏️ Пришлите исправленный текст ответа (на русском) ответным сообщением.");
        });
    }

    private Optional<ReviewTask> pendingReview(UUID emailId) {
        return reviews.findByEmailId(emailId)
                .filter(task -> task.getStatus() == ReviewStatus.PENDING);
    }

    /**
     * Пришёл текст правки (на русском). Переводим на язык собеседника,
     * сохраняем финал, показываем превью и отправляем.
     */
    @Transactional
    public boolean applyEditText(String editedRu, String reviewer) {
        ReviewTask task = reviews.findFirstByAwaitingEditTrueOrderByCreatedAtDesc().orElse(null);
        return task != null && finalizeEdit(task, editedRu, reviewer);
    }

    /** Правка из веб-очереди: адресно по письму (без телеграм-механики ожидания текста). */
    @Transactional
    public boolean applyEdit(UUID emailId, String editedRu, String reviewer) {
        ReviewTask task = pendingReview(emailId).orElse(null);
        return task != null && finalizeEdit(task, editedRu, reviewer);
    }

    /**
     * Общая финализация правки: переводим текст на язык собеседника, сохраняем финал,
     * закрываем задачу и публикуем одобрение. Обратная связь в Telegram — только для
     * телеграм-задач.
     */
    private boolean finalizeEdit(ReviewTask task, String editedRu, String reviewer) {
        Email email = emails.findById(task.getEmailId()).orElseThrow();
        Draft draft = drafts.findById(task.getDraftId()).orElseThrow();

        String managerLang = aiProps.getManagerLanguage();
        boolean needTranslation = email.getLanguage() != null
                && !managerLang.equalsIgnoreCase(email.getLanguage());

        String finalForClient = needTranslation
                ? translation.translate(editedRu, email.getLanguage())
                : editedRu;

        draft.setFinalText(finalForClient);
        drafts.save(draft);

        task.setAwaitingEdit(false);
        finishReview(task, ReviewStatus.EDITED, reviewer);
        transition(email.getId(), EmailStatus.EDITED);

        notifyTelegram(task, "Финальный текст (язык собеседника, %s):\n\n%s".formatted(
                nz(email.getLanguage()), trim(finalForClient)));
        events.publishEvent(new Events.ReviewApproved(email.getId()));
        return true;
    }

    private void finishReview(ReviewTask task, ReviewStatus status, String reviewer) {
        task.setStatus(status);
        task.setReviewer(reviewer);
        task.setDecidedAt(Instant.now());
        reviews.save(task);
    }

    private void transition(UUID emailId, EmailStatus status) {
        emails.findById(emailId).ifPresent(e -> {
            e.setStatus(status);
            emails.save(e);
        });
    }

    private String buildMessage(ReviewPayload p, String clientRu, boolean needTranslation) {
        StringBuilder sb = new StringBuilder();
        sb.append("📧 Новое письмо на ответ\n");
        sb.append("Ящик: ").append(nz(p.mailboxLabel())).append("\n");
        sb.append("От: ").append(nz(p.from())).append("\n");
        sb.append("Тема: ").append(nz(p.subject())).append("\n");
        sb.append("Язык: ").append(nz(p.language())).append("\n\n");

        sb.append("— Письмо —\n").append(trim(p.body())).append("\n\n");
        if (needTranslation && clientRu != null) {
            sb.append("— Перевод письма (RU) —\n").append(trim(clientRu)).append("\n\n");
        }
        sb.append("— Черновик ответа —\n").append(trim(p.draftText())).append("\n\n");
        if (needTranslation && p.draftTextRu() != null) {
            sb.append("— Перевод ответа (RU) —\n").append(trim(p.draftTextRu())).append("\n");
        }
        return sb.toString();
    }

    private String trim(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_BLOCK ? s.substring(0, MAX_BLOCK) + "…" : s;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}

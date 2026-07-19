package com.signet.review;

import com.signet.settings.SettingsChangedEvent;
import com.signet.settings.SettingsModel.TelegramSettings;
import com.signet.settings.SettingsService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Отправка сообщений менеджеру в Telegram. Токен и chat_id берутся из настроек
 * (UI) и применяются на лету — клиент пересобирается при их изменении.
 */
@Component
public class TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(TelegramGateway.class);

    private final SettingsService settings;

    private volatile TelegramClient client;
    private volatile String clientToken;

    public TelegramGateway(SettingsService settings) {
        this.settings = settings;
    }

    @EventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        if (SettingsChangedEvent.TELEGRAM.equals(event.area())) {
            synchronized (this) {
                client = null;
                clientToken = null;
            }
        }
    }

    /** Отправляет черновик на валидацию с inline-кнопками. */
    public Integer sendReview(String text, UUID emailId) {
        TelegramSettings tg = settings.telegram();
        TelegramClient c = clientFor(tg);
        if (c == null) {
            log.warn("Telegram не настроен — ревью {} не отправлено", emailId);
            return null;
        }
        InlineKeyboardRow row = new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("✅ Одобрить").callbackData("approve:" + emailId).build(),
                InlineKeyboardButton.builder().text("✏️ Редактировать").callbackData("edit:" + emailId).build(),
                InlineKeyboardButton.builder().text("❌ Отклонить").callbackData("reject:" + emailId).build());
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(List.of(row)).build();

        try {
            Message sent = c.execute(SendMessage.builder()
                    .chatId(String.valueOf(tg.managerChatId()))
                    .text(text)
                    .replyMarkup(markup)
                    .build());
            return sent.getMessageId();
        } catch (Exception ex) {
            log.error("Не удалось отправить ревью в Telegram: {}", ex.getMessage(), ex);
            return null;
        }
    }

    public void sendText(String text) {
        TelegramSettings tg = settings.telegram();
        TelegramClient c = clientFor(tg);
        if (c == null) {
            return;
        }
        try {
            c.execute(SendMessage.builder()
                    .chatId(String.valueOf(tg.managerChatId()))
                    .text(text)
                    .build());
        } catch (Exception ex) {
            log.error("Не удалось отправить сообщение в Telegram: {}", ex.getMessage());
        }
    }

    /** Отправляет вложение письма менеджеру документом. */
    public void sendDocument(byte[] data, String filename, String caption) {
        if (data == null || data.length == 0) {
            return;
        }
        TelegramSettings tg = settings.telegram();
        TelegramClient c = clientFor(tg);
        if (c == null) {
            return;
        }
        try {
            c.execute(SendDocument.builder()
                    .chatId(String.valueOf(tg.managerChatId()))
                    .document(new InputFile(new ByteArrayInputStream(data), filename))
                    .caption(caption)
                    .build());
        } catch (Exception ex) {
            log.error("Не удалось отправить вложение {} в Telegram: {}", filename, ex.getMessage());
        }
    }

    public void answerCallback(String callbackId) {
        TelegramClient c = clientFor(settings.telegram());
        if (c == null) {
            return;
        }
        try {
            c.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build());
        } catch (Exception ex) {
            log.debug("answerCallback: {}", ex.getMessage());
        }
    }

    /** Ленивая сборка клиента под текущий токен. */
    private TelegramClient clientFor(TelegramSettings tg) {
        if (!tg.isConfigured()) {
            return null;
        }
        TelegramClient local = client;
        if (local == null || !tg.botToken().equals(clientToken)) {
            synchronized (this) {
                if (client == null || !tg.botToken().equals(clientToken)) {
                    client = new OkHttpTelegramClient(tg.botToken());
                    clientToken = tg.botToken();
                }
                local = client;
            }
        }
        return local;
    }
}

package com.signet.review;

import com.signet.settings.SettingsChangedEvent;
import com.signet.settings.SettingsModel.TelegramSettings;
import com.signet.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

/**
 * Long-polling бот: принимает решения менеджера (approve/edit/reject) и текст правки.
 * Токен берётся из настроек (UI); при их изменении бот перезапускается.
 */
@Component
public class TelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final SettingsService settings;
    private final ReviewService reviewService;
    private final TelegramGateway gateway;

    private TelegramBotsLongPollingApplication botsApp;
    private String runningToken;

    public TelegramBot(SettingsService settings, ReviewService reviewService, TelegramGateway gateway) {
        this.settings = settings;
        this.reviewService = reviewService;
        this.gateway = gateway;
    }

    /** Стартуем после инициализации приложения (настройки уже засеяны). */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        TelegramSettings tg = settings.telegram();
        if (!tg.isConfigured()) {
            log.warn("Telegram не настроен — бот не запущен (настройте на /settings)");
            return;
        }
        if (botsApp != null && tg.botToken().equals(runningToken)) {
            return; // уже запущен с этим токеном
        }
        stop();
        try {
            botsApp = new TelegramBotsLongPollingApplication();
            botsApp.registerBot(tg.botToken(), this);
            runningToken = tg.botToken();
            log.info("Telegram-бот запущен (long polling)");
        } catch (Exception ex) {
            log.error("Не удалось запустить Telegram-бота: {}", ex.getMessage(), ex);
        }
    }

    @EventListener
    public void onSettingsChanged(SettingsChangedEvent event) {
        if (SettingsChangedEvent.TELEGRAM.equals(event.area())) {
            log.info("Настройки Telegram изменены — перезапуск бота");
            start();
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (botsApp != null) {
            try {
                botsApp.close();
            } catch (Exception ex) {
                log.debug("Остановка Telegram-бота: {}", ex.getMessage());
            }
            botsApp = null;
            runningToken = null;
        }
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleText(update);
            }
        } catch (Exception ex) {
            log.error("Ошибка обработки update Telegram: {}", ex.getMessage(), ex);
        }
    }

    private void handleCallback(Update update) {
        var callback = update.getCallbackQuery();
        long chatId = callback.getMessage().getChat().getId();
        if (!isManager(chatId)) {
            return;
        }
        String reviewer = username(callback.getFrom());
        String data = callback.getData();               // "action:emailId"
        gateway.answerCallback(callback.getId());

        int sep = data.indexOf(':');
        if (sep < 0) {
            return;
        }
        String action = data.substring(0, sep);
        UUID emailId = UUID.fromString(data.substring(sep + 1));

        switch (action) {
            case "approve" -> reviewService.approve(emailId, reviewer);
            case "reject" -> reviewService.reject(emailId, reviewer);
            case "edit" -> reviewService.requestEdit(emailId);
            default -> log.debug("Неизвестное действие: {}", action);
        }
    }

    private void handleText(Update update) {
        var message = update.getMessage();
        if (!isManager(message.getChatId())) {
            return;
        }
        String text = message.getText();
        if (text.startsWith("/")) {
            return;
        }
        boolean applied = reviewService.applyEditText(text, username(message.getFrom()));
        if (!applied) {
            gateway.sendText("Нет активной правки. Нажмите «✏️ Редактировать» под нужным письмом.");
        }
    }

    private boolean isManager(long chatId) {
        return chatId == settings.telegram().managerChatId();
    }

    private String username(User user) {
        if (user == null) {
            return "manager";
        }
        return user.getUserName() != null ? user.getUserName() : String.valueOf(user.getId());
    }
}

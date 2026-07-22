package com.signet.settings;

import com.signet.settings.SettingsModel.AiSettings;
import com.signet.settings.SettingsModel.TelegramSettings;
import com.signet.shared.config.Mailbox;
import com.signet.shared.domain.ReviewChannel;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON API настроек интеграций: Telegram, AI, промпт, опрос почты, почтовые ящики. */
@RestController
@RequestMapping("/api")
public class SettingsController {

    private final SettingsService settings;
    private final MailboxRegistry mailboxes;

    public SettingsController(SettingsService settings, MailboxRegistry mailboxes) {
        this.settings = settings;
        this.mailboxes = mailboxes;
    }

    // --- Чтение всех настроек одним запросом ---

    @GetMapping("/settings")
    public SettingsView all() {
        TelegramSettings tg = settings.telegram();
        AiSettings ai = settings.ai();
        List<MailboxView> boxes = mailboxes.entities().stream().map(MailboxView::of).toList();
        return new SettingsView(
                new TelegramView(tg.managerChatId(), tg.enabled(),
                        tg.botToken() != null && !tg.botToken().isBlank(), tg.isConfigured()),
                new AiView(ai.provider(), ai.ollamaBaseUrl(), ai.model(), ai.temperature(),
                        ai.systemPrompt(), ai.openAiApiKey() != null && !ai.openAiApiKey().isBlank()),
                boxes);
    }

    // --- Запись отдельных секций (пустой секрет = «не менять») ---
    // Опроса почты больше нет: синк ручной (кнопка в «Почте») + один раз при старте.

    @PostMapping("/settings/telegram")
    public ResponseEntity<Void> saveTelegram(@RequestBody TelegramReq req) {
        settings.saveTelegram(req.botToken(), req.managerChatId(), req.enabled());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/settings/ai")
    public ResponseEntity<Void> saveAi(@RequestBody AiReq req) {
        settings.saveAi(req.provider(), req.openAiApiKey(), req.ollamaBaseUrl(), req.model(), req.temperature());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/settings/prompt")
    public ResponseEntity<Void> savePrompt(@RequestBody PromptReq req) {
        settings.savePrompt(req.systemPrompt());   // пусто = вернуть промпт по умолчанию
        return ResponseEntity.noContent().build();
    }

    // --- Почтовые ящики ---

    @PostMapping("/mailboxes")
    public ResponseEntity<Void> saveMailbox(@RequestBody MailboxReq req) {
        Mailbox m = new Mailbox();
        m.setId(req.id());
        m.setProfile(orEmpty(req.profile()));
        m.setUsername(orEmpty(req.username()));
        m.setImapHost(orEmpty(req.imapHost()));
        m.setImapPort(req.imapPort() == null ? 993 : req.imapPort());
        m.setFolder(req.folder() == null || req.folder().isBlank() ? "INBOX" : req.folder());
        m.setProcessedFolder(orEmpty(req.processedFolder()));
        m.setSmtpHost(orEmpty(req.smtpHost()));
        m.setSmtpPort(req.smtpPort() == null ? 587 : req.smtpPort());
        m.setSmtpSsl(req.smtpSsl());
        m.setSmtpStarttls(req.smtpStarttls());
        m.setSmtpAuth(req.smtpAuth());
        m.setReviewChannel(req.reviewChannel() == null ? ReviewChannel.TELEGRAM : req.reviewChannel());
        mailboxes.save(m, req.password());   // пустой пароль = оставить прежний
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mailboxes/{id}/enabled")
    public ResponseEntity<Void> toggle(@PathVariable String id, @RequestBody EnabledReq req) {
        mailboxes.setEnabled(id, req.enabled());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mailboxes/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        mailboxes.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    // --- DTO ответа ---

    record SettingsView(TelegramView telegram, AiView ai, List<MailboxView> mailboxes) {
    }

    record TelegramView(long managerChatId, boolean enabled, boolean botTokenSet, boolean configured) {
    }

    record AiView(String provider, String ollamaBaseUrl, String model, double temperature,
                  String systemPrompt, boolean openAiKeySet) {
    }

    record MailboxView(String id, String profile, String username,
                       String imapHost, int imapPort, String folder, String processedFolder,
                       String smtpHost, int smtpPort, boolean smtpSsl, boolean smtpStarttls,
                       boolean smtpAuth, boolean enabled, ReviewChannel reviewChannel) {

        static MailboxView of(MailboxEntity e) {
            return new MailboxView(e.getId(), e.getProfile(), e.getUsername(),
                    e.getImapHost(), e.getImapPort(), e.getFolder(), e.getProcessedFolder(),
                    e.getSmtpHost(), e.getSmtpPort(), e.isSmtpSsl(), e.isSmtpStarttls(),
                    e.isSmtpAuth(), e.isEnabled(), e.getReviewChannel());
        }
    }

    // --- DTO запросов ---

    record TelegramReq(String botToken, long managerChatId, boolean enabled) {
    }

    record AiReq(String provider, String openAiApiKey, String ollamaBaseUrl, String model, double temperature) {
    }

    record PromptReq(String systemPrompt) {
    }

    record MailboxReq(String id, String profile, String username, String password,
                      String imapHost, Integer imapPort, String folder, String processedFolder,
                      String smtpHost, Integer smtpPort, boolean smtpSsl, boolean smtpStarttls,
                      boolean smtpAuth, ReviewChannel reviewChannel) {
    }

    record EnabledReq(boolean enabled) {
    }
}

package com.signet.settings;

import com.signet.shared.config.Mailbox;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Веб-настройка интеграций: Telegram, AI-провайдер, почтовые ящики. */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settings;
    private final MailboxRegistry mailboxes;

    public SettingsController(SettingsService settings, MailboxRegistry mailboxes) {
        this.settings = settings;
        this.mailboxes = mailboxes;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("tg", settings.telegram());
        model.addAttribute("ai", settings.ai());
        model.addAttribute("mailboxes", mailboxes.entities());
        model.addAttribute("polling", settings.polling());
        return "settings";
    }

    // --- Опрос почты ---

    @PostMapping("/polling")
    public String savePolling(@RequestParam(defaultValue = "45") int pollIntervalSeconds,
                              @RequestParam(defaultValue = "false") boolean windowEnabled,
                              @RequestParam(defaultValue = "") String windowZone,
                              @RequestParam(defaultValue = "") String windowDays,
                              @RequestParam(defaultValue = "08:00") String windowStart,
                              @RequestParam(defaultValue = "20:00") String windowEnd) {
        settings.savePolling(pollIntervalSeconds, windowEnabled, windowZone, windowDays, windowStart, windowEnd);
        return "redirect:/settings";
    }

    // --- Telegram ---

    @PostMapping("/telegram")
    public String saveTelegram(@RequestParam(defaultValue = "") String botToken,
                               @RequestParam(defaultValue = "0") long managerChatId,
                               @RequestParam(defaultValue = "false") boolean enabled) {
        settings.saveTelegram(botToken, managerChatId, enabled);
        return "redirect:/settings";
    }

    // --- AI ---

    @PostMapping("/ai")
    public String saveAi(@RequestParam String provider,
                         @RequestParam(defaultValue = "") String openAiApiKey,
                         @RequestParam(defaultValue = "") String ollamaBaseUrl,
                         @RequestParam(defaultValue = "") String model,
                         @RequestParam(defaultValue = "0.3") double temperature) {
        settings.saveAi(provider, openAiApiKey, ollamaBaseUrl, model, temperature);
        return "redirect:/settings";
    }

    // --- Промпт ---

    @PostMapping("/prompt")
    public String savePrompt(@RequestParam(defaultValue = "") String systemPrompt) {
        settings.savePrompt(systemPrompt);   // пусто = вернуть промпт по умолчанию
        return "redirect:/settings";
    }

    // --- Ящики ---

    @GetMapping("/mailbox")
    public String mailboxForm(@RequestParam(required = false) String id, Model model) {
        model.addAttribute("mailbox", id == null ? null : mailboxes.entity(id).orElse(null));
        return "mailbox-form";
    }

    @PostMapping("/mailbox")
    public String saveMailbox(@RequestParam String id,
                              @RequestParam(defaultValue = "") String profile,
                              @RequestParam(defaultValue = "") String username,
                              @RequestParam(defaultValue = "") String password,
                              @RequestParam(defaultValue = "") String imapHost,
                              @RequestParam(defaultValue = "993") int imapPort,
                              @RequestParam(defaultValue = "INBOX") String folder,
                              @RequestParam(defaultValue = "") String processedFolder,
                              @RequestParam(defaultValue = "") String smtpHost,
                              @RequestParam(defaultValue = "587") int smtpPort,
                              @RequestParam(defaultValue = "false") boolean smtpSsl,
                              @RequestParam(defaultValue = "false") boolean smtpStarttls,
                              @RequestParam(defaultValue = "false") boolean smtpAuth) {
        Mailbox m = new Mailbox();
        m.setId(id);
        m.setProfile(profile);
        m.setUsername(username);
        m.setImapHost(imapHost);
        m.setImapPort(imapPort);
        m.setFolder(folder);
        m.setProcessedFolder(processedFolder);
        m.setSmtpHost(smtpHost);
        m.setSmtpPort(smtpPort);
        m.setSmtpSsl(smtpSsl);
        m.setSmtpStarttls(smtpStarttls);
        m.setSmtpAuth(smtpAuth);
        mailboxes.save(m, password);   // пустой пароль = оставить прежний
        return "redirect:/settings";
    }

    @PostMapping("/mailbox/{id}/toggle")
    public String toggle(@PathVariable String id, @RequestParam boolean enabled) {
        mailboxes.setEnabled(id, enabled);
        return "redirect:/settings";
    }

    @PostMapping("/mailbox/{id}/delete")
    public String delete(@PathVariable String id) {
        mailboxes.delete(id);
        return "redirect:/settings";
    }
}

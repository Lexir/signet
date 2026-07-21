package com.signet.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Клиентские маршруты SPA. Хард-релоад на {@code /settings} или {@code /mailbox/gmail}
 * должен вернуть {@code index.html}, а роутингом займётся React. Корень {@code /} и статику
 * Spring Boot отдаёт сам; {@code /api/**} сюда не попадает.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/dashboard", "/settings", "/mailbox", "/mailbox/{id}",
            "/mail", "/mail/{mailboxId}", "/reviews"})
    public String forward() {
        return "forward:/index.html";
    }
}

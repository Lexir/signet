package com.signet.config;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Текущий статус аутентификации — SPA дёргает на старте, чтобы решить: логин или приложение. */
@RestController
public class AuthController {

    @GetMapping("/api/me")
    public Map<String, Object> me(Principal principal) {
        boolean authenticated = principal != null;
        return Map.of(
                "authenticated", authenticated,
                "username", authenticated ? principal.getName() : "");
    }
}

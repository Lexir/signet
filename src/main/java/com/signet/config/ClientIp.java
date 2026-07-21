package com.signet.config;

import jakarta.servlet.http.HttpServletRequest;

/** Определение IP клиента с опциональным учётом X-Forwarded-For (только за доверенным прокси). */
final class ClientIp {

    private ClientIp() {
    }

    static String of(HttpServletRequest request, AppSecurityProperties props) {
        if (props.isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}

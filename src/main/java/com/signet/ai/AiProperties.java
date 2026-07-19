package com.signet.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Сколько последних реплик треда отдавать модели. */
    private int historyWindow = 12;

    /** Язык перевода для валидации менеджером. */
    private String managerLanguage = "ru";

    /** Отвечать только на личные письма людей (отсекать компании/рассылки/спам через LLM). */
    private boolean onlyHumanSenders = true;

    public int getHistoryWindow() {
        return historyWindow;
    }

    public void setHistoryWindow(int historyWindow) {
        this.historyWindow = historyWindow;
    }

    public String getManagerLanguage() {
        return managerLanguage;
    }

    public void setManagerLanguage(String managerLanguage) {
        this.managerLanguage = managerLanguage;
    }

    public boolean isOnlyHumanSenders() {
        return onlyHumanSenders;
    }

    public void setOnlyHumanSenders(boolean onlyHumanSenders) {
        this.onlyHumanSenders = onlyHumanSenders;
    }
}

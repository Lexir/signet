package com.signet.analytics;

import com.signet.shared.domain.DailyStats;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Веб-дашборд (Thymeleaf) + JSON-эндпоинт метрик. */
@Controller
public class DashboardController {

    private final AnalyticsService analytics;

    public DashboardController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        AnalyticsSnapshot s = analytics.snapshot();
        model.addAttribute("s", s);
        model.addAttribute("labels", s.history().stream().map(d -> d.getDay().toString()).toList());
        model.addAttribute("received", s.history().stream().map(DailyStats::getReceived).toList());
        model.addAttribute("sent", s.history().stream().map(DailyStats::getSent).toList());
        return "dashboard";
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public AnalyticsSnapshot stats() {
        return analytics.snapshot();
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
}

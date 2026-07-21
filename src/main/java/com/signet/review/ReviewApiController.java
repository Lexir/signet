package com.signet.review;

import com.signet.review.ReviewService.ReviewQueueItem;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Веб-очередь ревью (канал UI): список ожидающих задач и решения approve/edit/reject. */
@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {

    private final ReviewService reviews;

    public ReviewApiController(ReviewService reviews) {
        this.reviews = reviews;
    }

    public record EditReq(String text) {
    }

    @GetMapping
    public List<ReviewQueueItem> pending() {
        return reviews.pendingUiQueue();
    }

    @PostMapping("/{emailId}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID emailId, Principal principal) {
        reviews.approve(emailId, reviewer(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{emailId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID emailId, Principal principal) {
        reviews.reject(emailId, reviewer(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{emailId}/edit")
    public ResponseEntity<Void> edit(@PathVariable UUID emailId, @RequestBody EditReq req, Principal principal) {
        boolean applied = req.text() != null && !req.text().isBlank()
                && reviews.applyEdit(emailId, req.text(), reviewer(principal));
        return applied ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
    }

    private String reviewer(Principal principal) {
        return principal != null ? principal.getName() : "ui";
    }
}

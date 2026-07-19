package com.signet.review;

import com.signet.shared.event.Events;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Слушает {@link Events.DraftReady} и открывает задачу ревью в мессенджере. */
@Component
public class DraftReadyListener {

    private final ReviewService reviewService;

    public DraftReadyListener(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @ApplicationModuleListener
    public void on(Events.DraftReady event) {
        reviewService.openReview(event.emailId(), event.draftId());
    }
}

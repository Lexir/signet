package com.signet.shared.repo;

import com.signet.shared.domain.ReviewChannel;
import com.signet.shared.domain.ReviewStatus;
import com.signet.shared.domain.ReviewTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewTaskRepository extends JpaRepository<ReviewTask, UUID> {

    Optional<ReviewTask> findByChatRef(String chatRef);

    Optional<ReviewTask> findByEmailId(UUID emailId);

    /** Единый менеджер: самая свежая задача, ждущая текст правки. */
    Optional<ReviewTask> findFirstByAwaitingEditTrueOrderByCreatedAtDesc();

    long countByStatus(ReviewStatus status);

    /** Очередь UI-ревью: ожидающие решения задачи выбранного канала. */
    List<ReviewTask> findByStatusAndChannelOrderByCreatedAtAsc(ReviewStatus status, ReviewChannel channel);

    /** Решения по ревью за период (по времени решения) — для дневных метрик дашборда. */
    long countByStatusAndDecidedAtAfter(ReviewStatus status, Instant after);

    /** Задачи, по которым давно нет решения и о которых ещё не напоминали. */
    List<ReviewTask> findByStatusAndRemindedAtIsNullAndCreatedAtBefore(ReviewStatus status, Instant before);

    /** Снимает ожидание правки со всех задач, кроме указанной. */
    @Modifying(clearAutomatically = true)
    @Query("update ReviewTask t set t.awaitingEdit = false where t.awaitingEdit = true and t.id <> :keepId")
    void clearAwaitingEditExcept(@Param("keepId") UUID keepId);
}

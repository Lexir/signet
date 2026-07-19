package com.signet.shared.repo;

import com.signet.shared.domain.DailyStats;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyStatsRepository extends JpaRepository<DailyStats, LocalDate> {

    List<DailyStats> findTop30ByOrderByDayAsc();
}

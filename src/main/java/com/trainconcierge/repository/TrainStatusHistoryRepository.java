package com.trainconcierge.repository;

import com.trainconcierge.entity.TrainStatusHistory;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainStatusHistoryRepository extends JpaRepository<TrainStatusHistory, Long> {

    /** History of one train and service date, oldest first (changedAt, then id). */
    List<TrainStatusHistory> findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(Long trainId,
                                                                                LocalDate serviceDate);
}

package com.im.agentic.domain.goal.repository;

import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalSlotType;
import com.im.agentic.domain.goal.entity.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserId(Long userId);
    List<Goal> findByUserIdAndStatus(Long userId, GoalStatus status);
    Optional<Goal> findByUserIdAndSlotTypeAndStatus(Long userId, GoalSlotType slotType, GoalStatus status);
}

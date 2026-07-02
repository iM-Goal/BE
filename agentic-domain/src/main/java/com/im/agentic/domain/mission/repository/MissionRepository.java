package com.im.agentic.domain.mission.repository;

import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.entity.MissionSource;
import com.im.agentic.domain.mission.entity.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    List<Mission> findByUserId(Long userId);
    List<Mission> findByUserIdAndStatus(Long userId, MissionStatus status);
    List<Mission> findByGoalId(Long goalId);
    List<Mission> findByUserIdAndMissionSource(Long userId, MissionSource source);
    List<Mission> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, MissionStatus status);
}

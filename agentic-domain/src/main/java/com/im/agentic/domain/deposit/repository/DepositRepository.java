package com.im.agentic.domain.deposit.repository;

import com.im.agentic.domain.deposit.entity.Deposit;
import com.im.agentic.domain.deposit.entity.DepositStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {
    Optional<Deposit> findByMissionId(Long missionId);
    List<Deposit> findByUserIdAndStatus(Long userId, DepositStatus status);
    List<Deposit> findByUserId(Long userId);
}

package com.im.agentic.domain.transaction.repository;

import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByTransactedAtDesc(Long userId);

    List<Transaction> findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.transactedAt >= :since AND t.category = :category")
    List<Transaction> findByUserIdAndCategoryAfter(
            @Param("userId") Long userId,
            @Param("category") SpendingCategory category,
            @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.category = :category " +
           "AND t.transactedAt >= :start")
    Long sumAmountByUserIdAndCategoryAfter(
            @Param("userId") Long userId,
            @Param("category") SpendingCategory category,
            @Param("start") LocalDateTime start);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user.id = :userId AND t.transactedAt >= :start")
    Long sumTodaySpend(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start);
}

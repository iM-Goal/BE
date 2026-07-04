package com.im.agentic.domain.mydata.repository;

import com.im.agentic.domain.mydata.entity.MyDataCardApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface MyDataCardApprovalRepository extends JpaRepository<MyDataCardApproval, Long> {

    @Query("SELECT m.category AS category, SUM(m.approvedAmt) AS total " +
           "FROM MyDataCardApproval m " +
           "WHERE m.userId = :userId " +
           "AND m.approvedDtime BETWEEN :start AND :end " +
           "AND m.deletedAt IS NULL " +
           "AND m.status = '01' " +
           "GROUP BY m.category")
    List<CategorySum> sumByCategoryBetween(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}

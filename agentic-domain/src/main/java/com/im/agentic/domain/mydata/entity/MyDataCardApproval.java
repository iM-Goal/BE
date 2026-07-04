package com.im.agentic.domain.mydata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "mydata_card_approvals")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MyDataCardApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "approved_num", nullable = false, length = 50)
    private String approvedNum;

    @Column(name = "approved_dtime", nullable = false)
    private LocalDateTime approvedDtime;

    @Column(name = "status", nullable = false, length = 2)
    private String status;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "approved_amt", nullable = false)
    private Long approvedAmt;

    @Column(name = "category", length = 20)
    private String category;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

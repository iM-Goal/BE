package com.im.agentic.api.mydata.dto;

public record CardApprovalRequest(
    String approvedNum,       // 승인번호
    String approvedDtime,     // 승인일시 YYYYMMDDhhmmss
    String status,            // 01:승인 02:취소
    String merchantName,      // 가맹점명
    Long approvedAmt          // 이용금액
) {}

package com.im.agentic.infra.openbanking.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class SpendingRecord {
    private String category;
    private Long amount;
    private String merchantName;
    private LocalDate date;
}

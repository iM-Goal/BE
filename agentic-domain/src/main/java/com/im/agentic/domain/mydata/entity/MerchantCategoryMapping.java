package com.im.agentic.domain.mydata.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "merchant_category_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MerchantCategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_name", nullable = false, unique = true, length = 100)
    private String merchantName;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "source", length = 100)
    private String source;
}

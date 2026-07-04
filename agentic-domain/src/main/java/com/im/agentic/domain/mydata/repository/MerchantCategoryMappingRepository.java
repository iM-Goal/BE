package com.im.agentic.domain.mydata.repository;

import com.im.agentic.domain.mydata.entity.MerchantCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MerchantCategoryMappingRepository extends JpaRepository<MerchantCategoryMapping, Long> {
    Optional<MerchantCategoryMapping> findByMerchantName(String merchantName);
}

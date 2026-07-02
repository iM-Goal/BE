package com.im.agentic.domain.mydata.repository;

import com.im.agentic.domain.mydata.entity.MyDataCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MyDataCacheRepository extends JpaRepository<MyDataCache, Long> {
    Optional<MyDataCache> findByUserId(Long userId);
}

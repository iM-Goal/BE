package com.im.agentic.api.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.agentic.api.mission.service.MissionService;
import com.im.agentic.domain.goal.entity.Goal;
import com.im.agentic.domain.goal.entity.GoalStatus;
import com.im.agentic.domain.goal.repository.GoalRepository;
import com.im.agentic.domain.mission.entity.Mission;
import com.im.agentic.domain.mission.entity.MissionStatus;
import com.im.agentic.domain.mission.entity.SpendingCategory;
import com.im.agentic.domain.mission.repository.MissionRepository;
import com.im.agentic.domain.mydata.entity.MyDataCache;
import com.im.agentic.domain.mydata.repository.MyDataCacheRepository;
import com.im.agentic.domain.transaction.entity.Transaction;
import com.im.agentic.domain.transaction.repository.TransactionRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionScheduler {

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final MissionRepository missionRepository;
    private final MyDataCacheRepository myDataCacheRepository;
    private final TransactionRepository transactionRepository;
    private final MissionService missionService;
    private final ObjectMapper objectMapper;

    // 매일 07:00 아침 브리핑
    @Scheduled(cron = "0 0 7 * * *")
    public void sendMorningBriefing() {
        log.info("[Scheduler] 아침 브리핑 발송 시작");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            // FCM 토큰이 설정된 유저 대상 알림 브리핑 로그 처리
            if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
                log.info("[FCM] Send Morning Briefing to userId={}: 가용 잔액을 기반으로 오늘도 절약 생활을 시작해 보세요!", user.getId());
            }
        }
    }

    // 매일 23:00 하루 정산 + IN_PROGRESS 미션 판단
    @Scheduled(cron = "0 0 23 * * *")
    @Transactional
    public void dailySettlement() {
        log.info("[Scheduler] 하루 정산 및 미션 판단 시작");

        // 1. 모든 IN_PROGRESS 미션 중 endDate <= today인 것 판단
        List<User> users = userRepository.findAll();
        LocalDate today = LocalDate.now();

        for (User user : users) {
            List<Mission> activeMissions = missionRepository.findByUserIdAndStatus(user.getId(), MissionStatus.IN_PROGRESS);
            for (Mission mission : activeMissions) {
                if (mission.getEndDate() != null && !mission.getEndDate().isAfter(today)) {
                    try {
                        missionService.judgeMission(mission.getId());
                        log.info("[Scheduler] 미션 판단 완료: missionId={}", mission.getId());
                    } catch (Exception e) {
                        log.error("[Scheduler] 미션 판단 실패: missionId={}, error={}", mission.getId(), e.getMessage());
                    }
                }
            }

            // 2. ACTIVE goal 가진 유저 대상 직접 미션 초과 감지
            List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(user.getId(), GoalStatus.ACTIVE);
            for (Goal goal : activeGoals) {
                try {
                    missionService.detectDirectOverageAndPropose(user.getId(), goal.getId());
                    log.info("[Scheduler] 직접 미션 초과 감지 완료: userId={}", user.getId());
                } catch (Exception e) {
                    log.error("[Scheduler] 직접 미션 초과 감지 실패: userId={}, error={}", user.getId(), e.getMessage());
                }
            }
        }
    }

    // 매주 월요일 08:00 주간 리포트 + 자동 미션 감지
    @Scheduled(cron = "0 0 8 * * MON")
    @Transactional
    public void weeklyReport() {
        log.info("[Scheduler] 주간 리포트 및 자동 미션 감지 시작");

        List<User> users = userRepository.findAll();
        LocalDateTime startOfLastWeek = LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime endOfLastWeek = startOfLastWeek.plusDays(7).minusNanos(1);

        for (User user : users) {
            try {
                // 1. MyDataCache weeklyAvgExpenses 업데이트
                MyDataCache cache = myDataCacheRepository.findByUserId(user.getId()).orElse(null);
                if (cache != null) {
                    // 지난주의 카테고리별 소비 집계
                    List<Transaction> lastWeekTxs = transactionRepository
                            .findByUserIdAndTransactedAtBetweenOrderByTransactedAtDesc(user.getId(), startOfLastWeek, endOfLastWeek);

                    Map<String, Long> lastWeekExpenses = new HashMap<>();
                    for (Transaction tx : lastWeekTxs) {
                        if (tx.getCategory() != null) {
                            String korName = mapToKorean(tx.getCategory());
                            lastWeekExpenses.put(korName, lastWeekExpenses.getOrDefault(korName, 0L) + tx.getAmount());
                        }
                    }

                    Map<String, Long> existingAvg = new HashMap<>();
                    try {
                        existingAvg = objectMapper.readValue(cache.getWeeklyAvgExpenses(),
                                new TypeReference<Map<String, Long>>() {});
                    } catch (Exception e) {
                        log.error("[Scheduler] 기존 weeklyAvgExpenses 파싱 실패: {}", e.getMessage());
                    }

                    // 가중평균 처리 (지난주 소비 30% + 기존 평균 70%)
                    Map<String, Long> newAvg = new HashMap<>();
                    for (SpendingCategory cat : SpendingCategory.values()) {
                        String korName = mapToKorean(cat);
                        long currentWeekAmount = lastWeekExpenses.getOrDefault(korName, 0L);
                        long prevAvg = existingAvg.getOrDefault(korName, 0L);
                        long calculated = (long) (currentWeekAmount * 0.3 + prevAvg * 0.7);
                        newAvg.put(korName, calculated);
                    }

                    String newAvgJson = objectMapper.writeValueAsString(newAvg);
                    cache.update(cache.getCategoryExpenses(), newAvgJson, cache.getDailyAvailableAmount());
                    myDataCacheRepository.save(cache);
                    log.info("[Scheduler] weeklyAvgExpenses 업데이트 완료: userId={}", user.getId());
                }

                // 2. 자동 미션 감지 및 제안
                missionService.detectAutoOverageAndPropose(user.getId());
                log.info("[Scheduler] 자동 미션 감지 완료: userId={}", user.getId());

            } catch (Exception e) {
                log.error("[Scheduler] 주간 처리 실패: userId={}, error={}", user.getId(), e.getMessage());
            }
        }
    }

    private String mapToKorean(SpendingCategory category) {
        if (category == null) return "기타";
        return switch (category) {
            case CAFE -> "카페";
            case DELIVERY -> "배달음식";
            case CONVENIENCE -> "편의점";
            case FOOD -> "식비";
            case TRANSPORT -> "교통";
            case SHOPPING -> "쇼핑";
        };
    }
}

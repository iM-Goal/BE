package com.im.agentic.api.planning.service;

import com.im.agentic.api.planning.dto.PlanReportRequestDto;
import com.im.agentic.api.planning.dto.PlanReportResponseDto;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.domain.compliance.entity.ComplianceAuditLog;
import com.im.agentic.domain.compliance.repository.ComplianceAuditLogRepository;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import com.im.agentic.infra.agent.AgentClient;
import com.im.agentic.infra.agent.dto.ComplianceInspectOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanningReportService {

    private final UserRepository userRepository;
    private final ComplianceAuditLogRepository complianceAuditLogRepository;
    private final AgentClient agentClient;

    @Transactional
    public PlanReportResponseDto generatePlanReport(PlanReportRequestDto request) {
        log.info("[PlanningReport] 레포트 생성 요청: userId={}, rawInput={}", request.userId(), request.rawGoalInput());

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new AgenticException(ErrorCode.USER_NOT_FOUND));

        // 1. 유저 재정 지표 확보 (기본값 방어)
        long monthlyIncome = user.getMonthlyIncome() != null && user.getMonthlyIncome() > 0 ? user.getMonthlyIncome() : 2_800_000L;
        long fixedExpense = user.getFixedExpense() != null && user.getFixedExpense() > 0 ? user.getFixedExpense() : 1_100_000L;
        long disposableIncome = user.getDisposableIncome() != null && user.getDisposableIncome() > 0 ? user.getDisposableIncome() : (monthlyIncome - fixedExpense);
        long dailyAvailable = user.getDailyAvailable() != null && user.getDailyAvailable() > 0 ? user.getDailyAvailable() : (disposableIncome / 30);

        // 2. 목표 유형 판별 (자산형성형 vs 일반소비형)
        String rawInput = request.rawGoalInput() != null ? request.rawGoalInput() : "";
        boolean isAssetAccumulation = isAssetGoal(rawInput, request.targetAmount());
        String planType = isAssetAccumulation ? "ASSET_ACCUMULATION" : "CONSUMPTION";

        // 3. 목표 금액 및 기간 산정
        long targetAmount = request.targetAmount() != null && request.targetAmount() > 0
                ? request.targetAmount()
                : (isAssetAccumulation ? 10_000_000L : 1_500_000L);

        int durationDays = request.durationDays() != null && request.durationDays() > 0
                ? request.durationDays()
                : (isAssetAccumulation ? 365 : 90);

        String goalName = extractGoalName(rawInput, isAssetAccumulation);
        LocalDate completionDate = LocalDate.now().plusDays(durationDays);
        String formattedDate = completionDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일"));

        // 4. 재무 및 예산 지표 계산 (dailyAvailable 기준)
        long dailySavingTarget = targetAmount / durationDays;
        long adjustedDailyBudget = Math.max(0, dailyAvailable - dailySavingTarget);
        long recommendedMonthlySavings = dailySavingTarget * 30;
        double savingCapacityRatio = disposableIncome > 0 ? Math.round((double) recommendedMonthlySavings / disposableIncome * 1000.0) / 10.0 : 35.0;

        String difficultyLevel;
        if (savingCapacityRatio <= 35.0) {
            difficultyLevel = "안정";
        } else if (savingCapacityRatio <= 50.0) {
            difficultyLevel = "보통";
        } else {
            difficultyLevel = "도전";
        }

        String recommendedProduct = isAssetAccumulation ? "iM 주거래 우대적금" : null;

        // 5. 내부통제 AI (Compliance Guardrail) 교차 검증 호출
        ComplianceInspectOutput compliance = agentClient.inspectCompliance(
                user.getId(),
                goalName,
                planType,
                monthlyIncome,
                disposableIncome,
                dailyAvailable,
                recommendedMonthlySavings,
                recommendedProduct
        );

        // 6. 내부통제 감사 로그 영구 적재 (책무구조도 대비)
        complianceAuditLogRepository.save(ComplianceAuditLog.builder()
                .userId(user.getId())
                .actionType("PLAN_REPORT_GENERATION")
                .planType(planType)
                .inputText(rawInput)
                .recommendedProduct(recommendedProduct)
                .isCompliant(compliance.isCompliant())
                .riskLevel(compliance.getRiskLevel())
                .requiresHumanReview(compliance.isRequiresHumanReview())
                .humanReviewReason(compliance.getReviewReason())
                .matchedRules(String.join(", ", compliance.getMatchedRules()))
                .complianceFootnote(compliance.getComplianceFootnote())
                .build());

        // 7. 레포트 블록 조립
        PlanReportResponseDto.GoalOverview goalOverview = PlanReportResponseDto.GoalOverview.builder()
                .goalName(goalName)
                .targetAmount(targetAmount)
                .durationDays(durationDays)
                .estimatedCompletionDate(formattedDate)
                .build();

        PlanReportResponseDto.IncomeSavingCapacity capacity = PlanReportResponseDto.IncomeSavingCapacity.builder()
                .monthlyIncome(monthlyIncome)
                .fixedExpense(fixedExpense)
                .disposableIncome(disposableIncome)
                .recommendedMonthlySavings(recommendedMonthlySavings)
                .savingCapacityRatio(savingCapacityRatio)
                .build();

        PlanReportResponseDto.DailyBudgetMetric dailyMetric = PlanReportResponseDto.DailyBudgetMetric.builder()
                .normalDailyAvailable(dailyAvailable)
                .dailySavingTarget(dailySavingTarget)
                .adjustedDailyBudget(adjustedDailyBudget)
                .difficultyLevel(difficultyLevel)
                .build();

        PlanReportResponseDto.FinancialProductRecommendation productRec = null;
        if (isAssetAccumulation) {
            long interestBenefit = Math.round(recommendedMonthlySavings * 12 * 0.0435 * 0.846 / 2);
            long maturityAmount = recommendedMonthlySavings * 12 + interestBenefit;

            productRec = PlanReportResponseDto.FinancialProductRecommendation.builder()
                    .productName("iM 주거래 우대적금")
                    .maxInterestRate("최고 연 4.35%")
                    .baseInterestRate("기본 연 3.10%")
                    .monthlyDeposit(recommendedMonthlySavings)
                    .expectedMaturityAmount(maturityAmount)
                    .expectedInterestBenefit(interestBenefit)
                    .daysShortened(18)
                    .actionButtonText(compliance.isRequiresHumanReview()
                            ? "전담 상담원(PB) 1:1 상담 연결하기"
                            : "약관 확인 및 2차 인증 후 가입하기")
                    .build();
        }

        PlanReportResponseDto.SpendingPatternAnalysis spendingAnalysis;
        if (isAssetAccumulation) {
            spendingAnalysis = PlanReportResponseDto.SpendingPatternAnalysis.builder()
                    .characterType("장바구니 헌터 표범 🐆")
                    .characterDescription("세일 알림에 취약하며 온라인 쇼핑 및 편의점 충동지출 빈도가 높은 유형")
                    .bossMonsterName("월 32만 원 장바구니 몬스터")
                    .questDescription("장바구니 24시간 보류 룰 적용으로 충동지출 차단하기")
                    .actionSolutions(List.of(
                            "장바구니 24시간 보류 룰 적용 (월 -15만 원 절감)",
                            "야식 및 편의점 횟수 50% 축소 (월 -8만 원 절감)",
                            "급여일 적금 선이체 자동 배정 (월 " + String.format("%,d", recommendedMonthlySavings) + "원)"
                    ))
                    .rewardBadge("FCPS +25점 & 목돈 달성 부스터 가동")
                    .build();
        } else {
            spendingAnalysis = PlanReportResponseDto.SpendingPatternAnalysis.builder()
                    .characterType("야행성 배달 올빼미 🦉")
                    .characterDescription("최근 4주간 배달 및 외식 지출이 전체 변동 소비의 42%를 차지하는 유형")
                    .bossMonsterName("월 38만 원 배달 몬스터")
                    .questDescription("배달음식 주 3회 ➔ 1회 축소로 방해 몬스터 격퇴하기")
                    .actionSolutions(List.of(
                            "배달음식 주 2회 줄이기 (하루 +9,000원 세이브)",
                            "카페 텀블러 할인 챌린지 (하루 +3,600원 세이브)",
                            "급여일 iMKRW 스마트 락업 자동이체 (하루 +4,000원 세이브)"
                    ))
                    .rewardBadge("FCPS +18점 & 완등 티켓 발권")
                    .build();
        }

        return PlanReportResponseDto.builder()
                .planType(planType)
                .goalOverview(goalOverview)
                .incomeSavingCapacity(capacity)
                .dailyBudgetMetric(dailyMetric)
                .financialProduct(productRec)
                .spendingAnalysis(spendingAnalysis)
                .complianceFootnote(compliance.getComplianceFootnote())
                .requiresHumanReview(compliance.isRequiresHumanReview())
                .humanReviewReason(compliance.getReviewReason())
                .build();
    }

    private boolean isAssetGoal(String rawInput, Long targetAmount) {
        if (targetAmount != null && targetAmount >= 5_000_000L) {
            return true;
        }
        String lower = rawInput.toLowerCase();
        return lower.contains("적금") || lower.contains("전세") || lower.contains("청약")
                || lower.contains("종잣돈") || lower.contains("목돈") || lower.contains("비상금")
                || lower.contains("천만원") || lower.contains("투자") || lower.contains("보증금")
                || lower.contains("예금");
    }

    private String extractGoalName(String rawInput, boolean isAsset) {
        if (rawInput == null || rawInput.isBlank()) {
            return isAsset ? "1년 1,000만 원 종잣돈 마련" : "맥북 에어 M3";
        }
        if (rawInput.contains("맥북")) return "맥북 에어 M3";
        if (rawInput.contains("에어팟")) return "에어팟 프로";
        if (rawInput.contains("여행")) return "유럽 여행 자금";
        if (rawInput.contains("적금") || rawInput.contains("목돈") || rawInput.contains("종잣돈")) return "1,000만 원 종잣돈 마련";
        if (rawInput.contains("전세")) return "전세 보증금 마련";
        return rawInput.length() > 30 ? rawInput.substring(0, 30) : rawInput;
    }
}

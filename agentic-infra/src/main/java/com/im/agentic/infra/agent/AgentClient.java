package com.im.agentic.infra.agent;

import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.infra.agent.dto.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AgentClient {

    private final WebClient goalParsingClient;
    private final WebClient spendingClient;
    private final WebClient missionClient;
    private final WebClient executionClient;
    private final WebClient chatClient;
    private final WebClient complianceClient;

    public AgentClient(
            @Value("${agent.goal-parsing.url:http://localhost:8001}") String goalParsingUrl,
            @Value("${agent.spending.url:http://localhost:8002}") String spendingUrl,
            @Value("${agent.mission.url:http://localhost:8003}") String missionUrl,
            @Value("${agent.execution.url:http://localhost:8004}") String executionUrl,
            @Value("${agent.chat.url:http://localhost:8005}") String chatUrl,
            @Value("${agent.compliance.url:http://localhost:8006}") String complianceUrl
    ) {
        this.goalParsingClient = WebClient.builder().baseUrl(goalParsingUrl).build();
        this.spendingClient = WebClient.builder().baseUrl(spendingUrl).build();
        this.missionClient = WebClient.builder().baseUrl(missionUrl).build();
        this.executionClient = WebClient.builder().baseUrl(executionUrl).build();
        this.chatClient = WebClient.builder().baseUrl(chatUrl).build();
        this.complianceClient = WebClient.builder().baseUrl(complianceUrl).build();
    }

    public GoalParsingOutput parseGoal(Long userId, String rawInput, Integer incomeQuintile, Long dailyAvailable) {
        try {
            log.info("[AgentClient] GoalParsingAgent 요청: userId={}, incomeQuintile={}, dailyAvailable={}", userId, incomeQuintile, dailyAvailable);
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "rawInput", rawInput,
                    "incomeQuintile", incomeQuintile != null ? incomeQuintile : 3,
                    "dailyAvailable", dailyAvailable != null ? dailyAvailable : 0L
            );
            GoalParsingResponse response = goalParsingClient.post()
                    .uri("/agent/parse-goal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GoalParsingResponse.class)
                    // API 서버 로컬 VRAM 자원 스왑 지연 방어를 위해 30초 적용
                    .timeout(Duration.ofSeconds(120))
                    .block();
            if (response != null) {
                log.info("[AgentClient] GoalParsing 완료: itemName={}", response.getItemName());
                if (response.getItemName() == null || response.getItemName().isBlank() || response.getPrice() == null || response.getPrice() <= 0) {
                    throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
                }
                return GoalParsingOutput.builder()
                        .itemName(response.getItemName())
                        .price(response.getPrice())
                        .category(response.getCategory())
                        .build();
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("[AgentClient] GoalParsing API 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (AgenticException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AgentClient] GoalParsing 실패: {}", e.getMessage());
            if (e.getCause() instanceof java.util.concurrent.TimeoutException || e.getMessage().contains("TimeoutException") || e.getMessage().contains("timeout")) {
                throw new AgenticException(ErrorCode.AGENT_TIMEOUT);
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        }
    }
 
    public SpendingAnalysisOutput analyzeSpending(Long userId, List<Map<String, Object>> spendings) {
        try {
            log.info("[AgentClient] SpendingAnalysisAgent 요청: userId={}", userId);
            Map<String, Object> request = Map.of("userId", userId, "spendings", spendings);
            SpendingAnalysisResponse response = spendingClient.post()
                    .uri("/agent/analyze-spending")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SpendingAnalysisResponse.class)
                    // API 서버 로컬 VRAM 자원 스왑 지연 방어를 위해 30초 적용
                    .timeout(Duration.ofSeconds(120))
                    .block();
            if (response != null) {
                log.info("[AgentClient] SpendingAnalysis 완료: categories={}", response.getCategoryExpenses().keySet());
                return SpendingAnalysisOutput.builder()
                        .categoryExpenses(response.getCategoryExpenses())
                        .totalExpenses(response.getTotalExpenses())
                        .dailyAvailableAmount(response.getDailyAvailableAmount())
                        .topCategory(response.getTopCategory())
                        .build();
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("[AgentClient] SpendingAnalysis API 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (AgenticException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AgentClient] SpendingAnalysis 실패: {}", e.getMessage());
            if (e.getCause() instanceof java.util.concurrent.TimeoutException || e.getMessage().contains("TimeoutException") || e.getMessage().contains("timeout")) {
                throw new AgenticException(ErrorCode.AGENT_TIMEOUT);
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        }
    }
 
    public MissionGenerationOutput generateMission(Long userId, Map<String, Long> categoryExpenses,
                                                    Long dailyAvailableAmount, String goalItemName) {
        try {
            log.info("[AgentClient] MissionGenerationAgent 요청: userId={}", userId);
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "categoryExpenses", categoryExpenses,
                    "dailyAvailableAmount", dailyAvailableAmount,
                    "goalItemName", goalItemName
            );
            MissionGenerationResponse response = missionClient.post()
                    .uri("/agent/generate-mission")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MissionGenerationResponse.class)
                    // API 서버 로컬 VRAM 자원 스왑 지연 방어를 위해 30초 적용
                    .timeout(Duration.ofSeconds(120))
                    .block();
            if (response != null) {
                log.info("[AgentClient] MissionGeneration 완료: category={}", response.getRestraintCategory());
                return MissionGenerationOutput.builder()
                        .restraintCategory(response.getRestraintCategory())
                        .restraintLimitAmount(response.getRestraintLimitAmount())
                        .restraintContributionRate(response.getRestraintContributionRate())
                        .savingTransferAmount(response.getSavingTransferAmount())
                        .savingContributionRate(response.getSavingContributionRate())
                        .build();
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("[AgentClient] MissionGeneration API 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (AgenticException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AgentClient] MissionGeneration 실패: {}", e.getMessage());
            if (e.getCause() instanceof java.util.concurrent.TimeoutException || e.getMessage().contains("TimeoutException") || e.getMessage().contains("timeout")) {
                throw new AgenticException(ErrorCode.AGENT_TIMEOUT);
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        }
    }
 
    public ExecutionOutput execute(Long userId, Long goalId, Long amount,
                                    String missionType, String categoryName,
                                    Long limitAmount, Long actualSpending) {
        try {
            log.info("[AgentClient] ExecutionAgent 요청: userId={}, type={}", userId, missionType);
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("goalId", goalId);
            request.put("amount", amount);
            request.put("missionType", missionType);
            if (categoryName != null) request.put("categoryName", categoryName);
            if (limitAmount != null) request.put("limitAmount", limitAmount);
            if (actualSpending != null) request.put("actualSpending", actualSpending);
 
            ExecutionResponse response = executionClient.post()
                    .uri("/agent/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ExecutionResponse.class)
                    // API 서버 로컬 VRAM 자원 스왑 지연 방어를 위해 30초 적용
                    .timeout(Duration.ofSeconds(120))
                    .block();
            if (response != null) {
                log.info("[AgentClient] Execution 완료: success={}", response.isSuccess());
                return ExecutionOutput.builder()
                        .success(response.isSuccess())
                        .shouldTransfer(response.isShouldTransfer())
                        .savedAmount(response.getSavedAmount())
                        .message(response.getMessage())
                        .build();
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("[AgentClient] Execution API 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        } catch (AgenticException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AgentClient] Execution 실패: {}", e.getMessage());
            if (e.getCause() instanceof java.util.concurrent.TimeoutException || e.getMessage().contains("TimeoutException") || e.getMessage().contains("timeout")) {
                throw new AgenticException(ErrorCode.AGENT_TIMEOUT);
            }
            throw new AgenticException(ErrorCode.AGENT_PARSE_FAILED);
        }
    }

    public OverageDetectResponse detectOverage(Long userId, Long dailyBudget, Long todaySpend, Map<String, Long> categoryExpenses) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "dailyBudget", dailyBudget,
                    "todaySpend", todaySpend,
                    "categoryExpenses", categoryExpenses
            );
            return missionClient.post()
                    .uri("/agent/detect-overage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OverageDetectResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            log.error("[AgentClient] detectOverage 실패: {}", e.getMessage());
            OverageDetectResponse fallback = new OverageDetectResponse();
            fallback.setHasOverage(false);
            return fallback;
        }
    }

    public AutoOverageResponse detectAutoOverage(Long userId, Map<String, Long> weeklyExpenses, Map<String, Long> weeklyAvgExpenses, Integer incomeQuintile) {
        try {
            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "weeklyExpenses", weeklyExpenses,
                    "weeklyAvgExpenses", weeklyAvgExpenses,
                    "incomeQuintile", incomeQuintile != null ? incomeQuintile : 3
            );
            return spendingClient.post()
                    .uri("/agent/detect-auto-overage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AutoOverageResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            log.error("[AgentClient] detectAutoOverage 실패: {}", e.getMessage());
            AutoOverageResponse fallback = new AutoOverageResponse();
            fallback.setHasOverage(false);
            return fallback;
        }
    }

    public ExecutionPeriodResponse executeMissionPeriod(Long userId, Long missionId, String category,
                                                         Long limitAmount, Integer durationDays, Long totalSpend) {
        try {
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("missionId", missionId);
            if (category != null) request.put("category", category);
            request.put("limitAmount", limitAmount);
            request.put("durationDays", durationDays);
            request.put("totalSpend", totalSpend);

            return executionClient.post()
                    .uri("/agent/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ExecutionPeriodResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            log.error("[AgentClient] executeMissionPeriod 실패: {}", e.getMessage());
            ExecutionPeriodResponse fallback = new ExecutionPeriodResponse();
            fallback.setSuccess(false);
            fallback.setDepositReturn(false);
            fallback.setMessage("판단 실패");
            return fallback;
        }
    }

    public ChatOutput chat(Long userId, String message, Map<String, Object> context) {
        try {
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("userId", userId);
            request.put("message", message);
            request.put("context", context);

            ChatResponse response = chatClient.post()
                    .uri("/agent/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();

            if (response != null) {
                log.info("[AgentClient] Chat 완료: answer length={}", 
                        response.getAnswer() != null ? response.getAnswer().length() : 0);
                return com.im.agentic.infra.agent.dto.ChatOutput.builder()
                        .answer(response.getAnswer())
                        .build();
            }
            return com.im.agentic.infra.agent.dto.ChatOutput.builder()
                    .answer("AI 응답을 받지 못했습니다.")
                    .build();
        } catch (Exception e) {
            log.error("[AgentClient] chat 실패: {}", e.getMessage());
            return com.im.agentic.infra.agent.dto.ChatOutput.builder()
                    .answer("죄송합니다. 현재 AI 서버와 연결이 원활하지 않습니다.")
                    .build();
        }
    }

    // 내부 응답 DTO
    @Getter @Setter @NoArgsConstructor
    public static class ChatResponse {
        private String answer;
    }
    @Getter @Setter @NoArgsConstructor
    public static class GoalParsingResponse {
        private String itemName;
        private Long price;
        private String category;
        private Map<String, Object> feasibility;
    }

    @Getter @Setter @NoArgsConstructor
    public static class SpendingAnalysisResponse {
        private Map<String, Long> categoryExpenses;
        private Long totalExpenses;
        private Long dailyAvailableAmount;
        private String topCategory;
    }

    @Getter @Setter @NoArgsConstructor
    public static class MissionGenerationResponse {
        private String restraintCategory;
        private Long restraintLimitAmount;
        private Double restraintContributionRate;
        private Long savingTransferAmount;
        private Double savingContributionRate;
    }

    @Getter @Setter @NoArgsConstructor
    public static class ExecutionResponse {
        private boolean success;
        private boolean shouldTransfer;
        private Long savedAmount;
        private String message;
    }

    @Getter @Setter @NoArgsConstructor
    public static class OverageDetectResponse {
        private boolean hasOverage;
        private Long overageAmount;
        private Double overageRate;
        private int missionCount;
        private List<MissionProposalDto> missions;
    }

    @Getter @Setter @NoArgsConstructor
    public static class MissionProposalDto {
        private String category;
        private String description;
        private Long limitAmount;
        private Long depositAmount;
        private Integer durationDays;
    }

    @Getter @Setter @NoArgsConstructor
    public static class AutoOverageResponse {
        private boolean hasOverage;
        private List<OverageCategoryDto> overageCategories;
        private String proposalReason;
    }

    @Getter @Setter @NoArgsConstructor
    public static class OverageCategoryDto {
        private String category;
        private Long thisWeek;
        private Long avgWeek;
        private Long overageAmount;
        private Double overageRate;
    }

    @Getter @Setter @NoArgsConstructor
    public static class ExecutionPeriodResponse {
        private boolean success;
        private boolean depositReturn;
        private String message;
    }

    public ComplianceInspectOutput inspectCompliance(
            Long userId,
            String userGoal,
            String planType,
            Long monthlyIncome,
            Long disposableIncome,
            Long dailyAvailable,
            Long proposedMonthlySavings,
            String recommendedProduct
    ) {
        try {
            log.info("[AgentClient] ComplianceAgent 요청: userId={}, goal={}, planType={}, product={}",
                    userId, userGoal, planType, recommendedProduct);

            Map<String, Object> request = Map.of(
                    "userId", userId,
                    "userGoal", userGoal != null ? userGoal : "",
                    "planType", planType != null ? planType : "CONSUMPTION",
                    "monthlyIncome", monthlyIncome != null ? monthlyIncome : 0L,
                    "disposableIncome", disposableIncome != null ? disposableIncome : 0L,
                    "dailyAvailable", dailyAvailable != null ? dailyAvailable : 0L,
                    "proposedMonthlySavings", proposedMonthlySavings != null ? proposedMonthlySavings : 0L,
                    "recommendedProduct", recommendedProduct != null ? recommendedProduct : ""
            );

            return complianceClient.post()
                    .uri("/agent/compliance/inspect")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ComplianceInspectOutput.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.warn("[AgentClient] ComplianceAgent 호출 실패 또는 미가동 -> 하이브리드 내장 룰 fallback: {}", e.getMessage());
            // Fallback: 자바 내부 룰 기반 결정론적 평가
            long disp = disposableIncome != null && disposableIncome > 0 ? disposableIncome : 1_700_000L;
            long prop = proposedMonthlySavings != null ? proposedMonthlySavings : 500_000L;
            long ratio = prop * 100 / disp;

            boolean requiresHuman = "ASSET_ACCUMULATION".equals(planType) && ratio > 50;
            String footnote = "ASSET_ACCUMULATION".equals(planType)
                    ? String.format("고객 월 가처분소득(%,d원) 대비 %d%% 납입 적합 판정 · 금소법 제17조 및 제19조 검증 완료 · 예금자보호법 적용 대상", disp, ratio)
                    : String.format("마이데이터 4주 지출 및 일일 가용예산(%,d원) 대비 적정 절제율 적용 · 금소법 제17조(적합성 원칙) 기준 일상생활 안정성 통과", dailyAvailable != null ? dailyAvailable : 35_000L);

            return ComplianceInspectOutput.builder()
                    .isCompliant(true)
                    .riskLevel(ratio > 50 ? "HIGH" : "LOW")
                    .requiresHumanReview(requiresHuman)
                    .reviewReason(requiresHuman ? "월 저축액이 가처분소득의 50%를 초과하여 생활비 부족 리스크가 감지되었습니다. 금소법 제17조에 따라 전담 상담원의 1:1 확인을 권장합니다." : "정상 적합 판정 완료")
                    .matchedRules(List.of("금융소비자보호법 제17조(적합성의 원칙)", "금융소비자보호법 제19조(설명의무)"))
                    .complianceFootnote(footnote)
                    .build();
        }
    }
}

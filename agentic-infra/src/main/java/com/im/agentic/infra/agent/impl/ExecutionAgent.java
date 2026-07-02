package com.im.agentic.infra.agent.impl;

import com.im.agentic.infra.agent.Agent;
import com.im.agentic.infra.agent.dto.AgentInput;
import com.im.agentic.infra.openbanking.OpenBankingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class ExecutionAgent implements Agent<AgentInput, Boolean> {

    private final OpenBankingClient openBankingClient;

    @Override
    public String getName() { return "ExecutionAgent"; }

    @Override
    public Boolean execute(AgentInput input) {
        // 이체 실행
        boolean result = openBankingClient.transfer(
                input.getUserId(),
                input.getGoalId(),
                input.getDailyAvailableAmount()
        );
        log.info("[ExecutionAgent] userId={}, goalId={}, amount={}, result={}",
                input.getUserId(), input.getGoalId(),
                input.getDailyAvailableAmount(), result);
        return result;
    }
}

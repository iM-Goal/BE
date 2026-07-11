package com.im.agentic.api.chat.controller;

import com.im.agentic.api.chat.dto.ChatRequestDto.ChatRequest;
import com.im.agentic.api.chat.dto.ChatResponseDto.ChatResponse;
import com.im.agentic.api.chat.service.ChatService;
import com.im.agentic.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "AI 금융 상담 챗봇 API")
public class ChatController {

    private final ChatService chatService;

    @Operation(
        summary = "AI 챗봇 대화",
        description = """
            사용자의 질문을 받아 본인의 소비 데이터, 목표, 미션 현황을 기반으로
            AI가 개인화된 금융 조언을 자연어로 답변합니다.
            
            **포함 컨텍스트**
            - 재정 정보: 월소득, 처분가능소득, 일일 가용금액, 소득분위
            - 목표 현황: 슬롯별 상품명, 목표금액, 달성률
            - 미션 현황: 진행중/성공/실패, 카테고리, 한도금액
            - 보증금 총액
            - 카테고리별 소비 지출
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "질문 예시",
                    value = """
                        {
                            "message": "이번 달 카페 소비가 너무 많은 것 같아"
                        }
                        """
                )
            )
        )
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "AI 답변 반환 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 정보 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AI 에이전트 응답 실패")
    })
    @PostMapping
    public ApiResponse<ChatResponse> chat(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid ChatRequest request) {
        return ApiResponse.success(chatService.chat(userId, request.getMessage()));
    }
}

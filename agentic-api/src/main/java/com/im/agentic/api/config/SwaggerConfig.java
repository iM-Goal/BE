package com.im.agentic.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // JWT Bearer 인증 스키마 정의
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("로그인 후 발급받은 JWT 토큰을 입력하세요. 예: Bearer eyJhbGci...");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("BearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("iM AgentiX API")
                        .description("목표만 말하면 AI와 스마트 컨트랙트가 저축을 실행합니다. 직접 미션(목표 기반 가용 금액 역산)과 자동 미션(4주 평균 대비 과소비 감지) 두 가지 방식으로 보증금 기반 미션을 제안하고, iMKRW + Kaia 스마트 컨트랙트로 보증금을 관리합니다.")
                        .version("v2.0 (보증금 기반 미션 시스템)")
                        .contact(new Contact()
                                .name("박지현")
                                .email("dev@im-agentix.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                        new Server().url("https://api.im-agentix.com").description("프로덕션 서버")
                ))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", securityScheme))
                .addSecurityItem(securityRequirement)
                .tags(List.of(
                        new Tag().name("Auth").description("회원가입, 로그인, JWT 토큰 발급 API"),
                        new Tag().name("Goal").description("저축 목표 등록/조회/수정/삭제 및 Buy-out API"),
                        new Tag().name("Mission").description("직접 미션 / 자동 미션 관리 API"),
                        new Tag().name("Deposit").description("보증금(iMKRW 잠금) 관리 및 출금 마찰 플로우 API"),
                        new Tag().name("Spending").description("소비 추적 및 가용 금액 모니터링 API"),
                        new Tag().name("Dashboard").description("저축 현황 및 주간 리포트 API"),
                        new Tag().name("Transaction").description("소비 거래 내역 등록 및 조회 API"),
                        new Tag().name("Programmable Money").description("iMKRW 스테이블코인 발행/소각 API (보증금 전용)"),
                        new Tag().name("MyData").description("마이데이터 기반 소비 분석 및 캐시 조회 API")
                ));
    }
}

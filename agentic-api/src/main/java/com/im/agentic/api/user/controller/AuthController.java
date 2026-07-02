package com.im.agentic.api.user.controller;

import com.im.agentic.api.user.dto.UserRequestDto.LoginRequest;
import com.im.agentic.api.user.dto.UserRequestDto.SignUpRequest;
import com.im.agentic.api.user.dto.UserResponseDto.LoginResponse;
import com.im.agentic.api.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.im.agentic.common.response.ApiResponse.success;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입, 로그인, JWT 토큰 발급 API")
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "회원가입",
        description = """
            신규 사용자를 등록합니다.
            
            **이메일 중복 시 409 에러가 반환됩니다.**
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "회원가입 예시",
                    value = """
                        {
                            "email": "user@im.com",
                            "password": "test1234!"
                        }
                        """
                )
            )
        )
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "회원가입 성공"),
        @ApiResponse(responseCode = "409", description = "이메일 중복")
    })
    @SecurityRequirements  // 이 API는 인증 불필요
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public com.im.agentic.common.response.ApiResponse<Void> signUp(@RequestBody SignUpRequest request) {
        authService.signUp(request);
        return success(null);
    }

    @Operation(
        summary = "로그인",
        description = """
            이메일과 비밀번호로 로그인합니다.
            
            **응답의 `token` 값을 복사해 Authorize에 입력하세요.**
            
            모든 보호된 API 요청 시 Header에 `Authorization: Bearer {token}` 형식으로 포함해야 합니다.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    name = "로그인 예시",
                    value = """
                        {
                            "email": "user@im.com",
                            "password": "test1234!"
                        }
                        """
                )
            )
        )
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "로그인 성공 — token 값을 Authorize에 입력하세요",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                            "success": true,
                            "data": {
                                "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                            }
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "401", description = "비밀번호 불일치"),
        @ApiResponse(responseCode = "404", description = "존재하지 않는 이메일")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public com.im.agentic.common.response.ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return success(authService.login(request));
    }
}

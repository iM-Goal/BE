package com.im.agentic.api.user.controller;

import com.im.agentic.api.user.dto.UserResponseDto.ProfileResponse;
import com.im.agentic.api.user.service.UserService;
import com.im.agentic.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 프로필 관련 API")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필(닉네임, 재정정보 등)을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(userService.getMyProfile(userId));
    }
}

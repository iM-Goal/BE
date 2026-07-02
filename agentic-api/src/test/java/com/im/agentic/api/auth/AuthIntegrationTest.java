package com.im.agentic.api.auth;

import com.im.agentic.api.support.IntegrationTestBase;
import com.im.agentic.api.support.TestFixtures;
import com.im.agentic.api.user.dto.UserRequestDto.LoginRequest;
import com.im.agentic.api.user.dto.UserRequestDto.SignUpRequest;
import com.im.agentic.api.user.dto.UserResponseDto.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("회원가입 성공 → 201 반환")
    void signUp_withValidRequest_returns201() {
        // given
        SignUpRequest request = TestFixtures.signUpRequest();

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("이메일 중복 회원가입 → 409 반환")
    void signUp_withDuplicateEmail_returns409() {
        // given
        SignUpRequest request = TestFixtures.signUpRequest();
        restTemplate.postForEntity(baseUrl() + "/api/auth/signup", request, String.class);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup", request, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("로그인 성공 → JWT 토큰 반환")
    void login_withValidCredentials_returnsJwtToken() {
        // given
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup",
                TestFixtures.signUpRequest(), String.class);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                TestFixtures.loginRequest(), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = getResponseData(response, LoginResponse.class);
        assertThat(body).isNotNull();
        assertThat(body.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("잘못된 비밀번호 로그인 → 401 반환")
    void login_withWrongPassword_returns401() {
        // given
        restTemplate.postForEntity(
                baseUrl() + "/api/auth/signup",
                TestFixtures.signUpRequest(), String.class);

        LoginRequest wrongRequest = new LoginRequest();
        wrongRequest.setEmail(TestFixtures.TEST_EMAIL);
        wrongRequest.setPassword("wrongpassword");

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login", wrongRequest, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("인증 없이 보호된 API 접근 → 403 반환")
    void accessProtectedApi_withoutToken_returns403() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/goals", String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}


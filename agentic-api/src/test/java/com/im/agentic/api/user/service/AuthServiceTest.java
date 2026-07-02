package com.im.agentic.api.user.service;

import com.im.agentic.api.user.dto.UserRequestDto.LoginRequest;
import com.im.agentic.api.user.dto.UserRequestDto.SignUpRequest;
import com.im.agentic.api.user.dto.UserResponseDto.LoginResponse;
import com.im.agentic.common.exception.AgenticException;
import com.im.agentic.common.exception.ErrorCode;
import com.im.agentic.common.jwt.JwtUtil;
import com.im.agentic.domain.user.entity.User;
import com.im.agentic.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("회원가입 성공")
    void signUp_success() {
        SignUpRequest request = new SignUpRequest();
        request.setEmail("test@im.com");
        request.setPassword("test1234");

        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");

        assertThatNoException().isThrownBy(() -> authService.signUp(request));
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("이메일 중복 시 EMAIL_ALREADY_EXISTS 예외")
    void signUp_emailDuplicate() {
        SignUpRequest request = new SignUpRequest();
        request.setEmail("test@im.com");
        request.setPassword("test1234");

        User existingUser = User.builder()
                .id(1L)
                .email("test@im.com")
                .password("encoded")
                .build();
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(AgenticException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("로그인 성공 - JWT 토큰 반환")
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@im.com");
        request.setPassword("test1234");

        User user = User.builder()
                .id(1L)
                .email("test@im.com")
                .password("encodedPassword")
                .build();

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(jwtUtil.generateToken(any())).willReturn("mocked.jwt.token");

        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("mocked.jwt.token");
    }

    @Test
    @DisplayName("존재하지 않는 이메일 로그인 시 USER_NOT_FOUND 예외")
    void login_userNotFound() {
        LoginRequest request = new LoginRequest();
        request.setEmail("notexist@im.com");
        request.setPassword("test1234");

        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AgenticException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 불일치 시 UNAUTHORIZED 예외")
    void login_wrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@im.com");
        request.setPassword("wrongPassword");

        User user = User.builder()
                .id(1L)
                .email("test@im.com")
                .password("encodedPassword")
                .build();

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AgenticException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}

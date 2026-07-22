package me.aster.echochat.auth.service.impl;

import me.aster.echochat.auth.client.UserFeignClient;
import me.aster.echochat.auth.constant.AuthConstants;
import me.aster.echochat.auth.dto.DeviceInfo;
import me.aster.echochat.auth.dto.LoginRequest;
import me.aster.echochat.auth.dto.RegisterRequest;
import me.aster.echochat.auth.dto.TokenResponse;
import me.aster.echochat.auth.util.JwtUtil;
import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final Long UID = 1L;
    private static final String DEVICE_ID = "device-abc";
    private static final String PLATFORM = "web";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String REFRESH_TOKEN = "refresh-token-value";
    private static final String NICKNAME = "TestUser";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    // ---- 辅助方法 ----

    private User buildUser() {
        User user = new User();
        user.setUid(UID);
        user.setNickname(NICKNAME);
        user.setEmail("test@example.com");
        return user;
    }

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("secret123");
        req.setNickname(NICKNAME);
        req.setDeviceId(DEVICE_ID);
        req.setPlatform(PLATFORM);
        return req;
    }

    private LoginRequest buildLoginRequest() {
        LoginRequest req = new LoginRequest();
        req.setAccount("test@example.com");
        req.setPassword("secret123");
        req.setDeviceId(DEVICE_ID);
        req.setPlatform(PLATFORM);
        return req;
    }

    private void stubTokenGeneration() {
        when(jwtUtil.generateAccessToken(eq(UID), anyString())).thenReturn(ACCESS_TOKEN);
        when(jwtUtil.generateRefreshToken(eq(UID), anyString())).thenReturn(REFRESH_TOKEN);
    }

    private void stubStoreTokenAndDevice() {
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
    }

    private void assertTokenResponse(TokenResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.getExpiresIn()).isEqualTo(AuthConstants.ACCESS_TOKEN_EXPIRATION_SECONDS);
        assertThat(response.getUid()).isEqualTo(UID);
        assertThat(response.getNickname()).isEqualTo(NICKNAME);
    }

    // ===================================================================

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should create user via Feign and issue tokens")
        void shouldCreateUserAndIssueTokens() {
            RegisterRequest req = buildRegisterRequest();
            User user = buildUser();
            stubTokenGeneration();
            stubStoreTokenAndDevice();
            when(userFeignClient.createUser(any())).thenReturn(user);

            TokenResponse response = authService.register(req);

            assertTokenResponse(response);
            verify(userFeignClient).createUser(any());
            verify(jwtUtil).generateAccessToken(UID, DEVICE_ID);
            verify(jwtUtil).generateRefreshToken(UID, DEVICE_ID);
            verify(valueOperations, times(2)).set(anyString(), anyString(), any(Duration.class));
            // 登录/注册不得将用户标记为在线；只有活跃的WebSocket连接才标记在线。
            verify(setOperations, never()).add(anyString(), anyString());
            verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("should propagate exception when Feign createUser fails")
        void shouldPropagateExceptionOnDuplicateUser() {
            RegisterRequest req = buildRegisterRequest();
            when(userFeignClient.createUser(any()))
                    .thenThrow(new BusinessException(ResultCode.USER_ALREADY_EXISTS));

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(BusinessException.class);

            verify(jwtUtil, never()).generateAccessToken(anyLong(), anyString());
        }

        @Test
        @DisplayName("should generate deviceId when not provided in request")
        void shouldGenerateDeviceIdWhenNotProvided() {
            RegisterRequest req = buildRegisterRequest();
            req.setDeviceId(null);
            User user = buildUser();
            stubTokenGeneration();
            stubStoreTokenAndDevice();
            when(userFeignClient.createUser(any())).thenReturn(user);

            TokenResponse response = authService.register(req);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.getRefreshToken()).isEqualTo(REFRESH_TOKEN);
            verify(userFeignClient).createUser(any());
        }
    }

    // ===================================================================

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should verify password and issue tokens on success")
        void shouldVerifyPasswordAndIssueTokens() {
            LoginRequest req = buildLoginRequest();
            User user = buildUser();
            stubTokenGeneration();
            stubStoreTokenAndDevice();
            Map<String, Object> verifyResult = Map.of("valid", true, "uid", UID);
            when(userFeignClient.verifyPassword(any())).thenReturn(verifyResult);
            when(userFeignClient.getUserByUid(UID)).thenReturn(user);

            TokenResponse response = authService.login(req);

            assertTokenResponse(response);
            verify(userFeignClient).verifyPassword(any());
            verify(userFeignClient).getUserByUid(UID);
            verify(jwtUtil).generateAccessToken(UID, DEVICE_ID);
            verify(jwtUtil).generateRefreshToken(UID, DEVICE_ID);
        }

        @Test
        @DisplayName("should throw PASSWORD_ERROR when password is incorrect")
        void shouldThrowPasswordErrorOnWrongPassword() {
            LoginRequest req = buildLoginRequest();
            Map<String, Object> verifyResult = Map.of("valid", false);
            when(userFeignClient.verifyPassword(any())).thenReturn(verifyResult);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Incorrect password");

            verify(jwtUtil, never()).generateAccessToken(anyLong(), anyString());
        }

        @Test
        @DisplayName("should propagate exception when user is not found")
        void shouldPropagateExceptionWhenUserNotFound() {
            LoginRequest req = buildLoginRequest();
            when(userFeignClient.verifyPassword(any()))
                    .thenThrow(new RuntimeException("User not found"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ===================================================================

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("should issue new tokens when refresh token is valid")
        void shouldIssueNewTokensOnValidRefreshToken() {
            User user = buildUser();
            stubTokenGeneration();
            stubStoreTokenAndDevice();
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.getTokenType(REFRESH_TOKEN)).thenReturn("refresh");
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            when(jwtUtil.getUidFromToken(REFRESH_TOKEN)).thenReturn(UID);
            when(jwtUtil.getDeviceIdFromToken(REFRESH_TOKEN)).thenReturn(DEVICE_ID);
            when(userFeignClient.getUserByUid(UID)).thenReturn(user);
            when(jwtUtil.getTokenExpiration(REFRESH_TOKEN)).thenReturn(System.currentTimeMillis() + 60000L);

            TokenResponse response = authService.refresh(REFRESH_TOKEN);

            assertTokenResponse(response);
            verify(jwtUtil).validateToken(REFRESH_TOKEN);
            verify(userFeignClient).getUserByUid(UID);
            verify(jwtUtil).generateAccessToken(UID, DEVICE_ID);
            verify(jwtUtil).generateRefreshToken(UID, DEVICE_ID);
            verify(valueOperations).set(anyString(), eq(ACCESS_TOKEN), any(Duration.class));
        }

        @Test
        @DisplayName("should throw TOKEN_INVALID when token is expired or malformed")
        void shouldThrowTokenInvalidOnExpiredToken() {
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Token invalid");

            verify(jwtUtil, never()).getUidFromToken(anyString());
        }

        @Test
        @DisplayName("should throw TOKEN_INVALID when token type is not refresh")
        void shouldThrowTokenInvalidOnWrongTokenType() {
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.getTokenType(REFRESH_TOKEN)).thenReturn("access");

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Wrong token type");

            verify(jwtUtil, never()).getUidFromToken(anyString());
        }

        @Test
        @DisplayName("should throw TOKEN_INVALID when token is blacklisted")
        void shouldThrowTokenInvalidOnBlacklistedToken() {
            when(jwtUtil.validateToken(REFRESH_TOKEN)).thenReturn(true);
            when(jwtUtil.getTokenType(REFRESH_TOKEN)).thenReturn("refresh");
            when(redisTemplate.hasKey(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Token revoked");

            verify(jwtUtil, never()).getUidFromToken(anyString());
        }
    }

    // ===================================================================

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should blacklist token, clear Redis state, and mark offline")
        void shouldBlacklistTokenAndClearState() {
            when(jwtUtil.getDeviceIdFromToken(ACCESS_TOKEN)).thenReturn(DEVICE_ID);
            when(redisTemplate.delete(anyString())).thenReturn(true);
            when(zSetOperations.remove(anyString(), any())).thenReturn(1L);
            when(jwtUtil.getTokenExpiration(ACCESS_TOKEN)).thenReturn(System.currentTimeMillis() + 60000L);

            assertThatCode(() -> authService.logout(UID, ACCESS_TOKEN))
                    .doesNotThrowAnyException();

            verify(jwtUtil).getDeviceIdFromToken(ACCESS_TOKEN);
            verify(valueOperations).set(anyString(), eq("1"), any(Duration.class));
            verify(redisTemplate).delete("token:access:1:" + DEVICE_ID);
            verify(redisTemplate).delete("token:refresh:1:" + DEVICE_ID);
            verify(zSetOperations).remove("user:online:1", DEVICE_ID);
            verify(userFeignClient).markOffline(eq(UID), any());
        }

        @Test
        @DisplayName("should not throw when markOffline fails")
        void shouldNotThrowWhenMarkOfflineFails() {
            when(jwtUtil.getDeviceIdFromToken(ACCESS_TOKEN)).thenReturn(DEVICE_ID);
            when(redisTemplate.delete(anyString())).thenReturn(true);
            when(zSetOperations.remove(anyString(), any())).thenReturn(1L);
            when(jwtUtil.getTokenExpiration(ACCESS_TOKEN)).thenReturn(System.currentTimeMillis() + 60000L);
            doThrow(new RuntimeException("Network error")).when(userFeignClient).markOffline(eq(1L), any());

            assertThatCode(() -> authService.logout(UID, ACCESS_TOKEN))
                    .doesNotThrowAnyException();

            verify(userFeignClient).markOffline(eq(UID), any());
        }
    }

    // ===================================================================

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("should delegate to Feign and invalidate all tokens")
        void shouldDelegateToFeignAndInvalidateTokens() {
            when(hashOperations.keys("user:devices:" + UID)).thenReturn(Set.of());
            when(redisTemplate.delete(anyString())).thenReturn(true);

            assertThatCode(() -> authService.changePassword(UID, "oldPass", "newPass"))
                    .doesNotThrowAnyException();

            verify(userFeignClient).changePassword(eq(UID), any());
            verify(valueOperations).set(eq("user:last_seen:" + UID), anyString(), any(Duration.class));
            verify(redisTemplate).delete("user:devices:" + UID);
            verify(redisTemplate).delete("user:online:" + UID);
        }
    }

    // ===================================================================

    @Nested
    @DisplayName("getDevices")
    class GetDevices {

        @Test
        @DisplayName("should return device list with online status")
        void shouldReturnDeviceListWithOnlineStatus() {
            Set<String> onlineDevices = Set.of(DEVICE_ID);
            when(zSetOperations.rangeByScore(eq("user:online:" + UID), anyDouble(), anyDouble()))
                    .thenReturn(onlineDevices);

            Map<Object, Object> deviceMap = Map.of(DEVICE_ID,
                    "{\"platform\":\"web\",\"loginAt\":\"2026-07-18 12:00:00\"}");
            when(hashOperations.entries("user:devices:" + UID)).thenReturn(deviceMap);

            List<DeviceInfo> devices = authService.getDevices(UID);

            assertThat(devices).hasSize(1);
            DeviceInfo info = devices.get(0);
            assertThat(info.getDeviceId()).isEqualTo(DEVICE_ID);
            assertThat(info.getPlatform()).isEqualTo(PLATFORM);
            assertThat(info.getOnline()).isTrue();
            assertThat(info.getCurrent()).isFalse();
            verify(zSetOperations).rangeByScore(eq("user:online:" + UID), anyDouble(), anyDouble());
            verify(hashOperations).entries("user:devices:" + UID);
        }

        @Test
        @DisplayName("should return empty list when no devices exist")
        void shouldReturnEmptyListWhenNoDevices() {
            when(zSetOperations.rangeByScore(eq("user:online:" + UID), anyDouble(), anyDouble()))
                    .thenReturn(Set.of());
            when(hashOperations.entries("user:devices:" + UID)).thenReturn(Collections.emptyMap());

            List<DeviceInfo> devices = authService.getDevices(UID);

            assertThat(devices).isEmpty();
        }
    }

    // ===================================================================

    @Nested
    @DisplayName("kickDevice")
    class KickDevice {

        @Test
        @DisplayName("should blacklist tokens, remove device state, and mark offline")
        void shouldBlacklistTokensAndRemoveDevice() {
            String accessKey = "token:access:" + UID + ":" + DEVICE_ID;
            String refreshKey = "token:refresh:" + UID + ":" + DEVICE_ID;
            when(valueOperations.get(accessKey)).thenReturn(ACCESS_TOKEN);
            when(valueOperations.get(refreshKey)).thenReturn(REFRESH_TOKEN);
            when(jwtUtil.getTokenExpiration(ACCESS_TOKEN)).thenReturn(System.currentTimeMillis() + 60000L);
            when(jwtUtil.getTokenExpiration(REFRESH_TOKEN)).thenReturn(System.currentTimeMillis() + 60000L);
            when(redisTemplate.delete(anyString())).thenReturn(true);
            when(zSetOperations.remove(anyString(), any())).thenReturn(1L);
            when(hashOperations.delete(anyString(), anyString())).thenReturn(1L);

            assertThatCode(() -> authService.kickDevice(UID, DEVICE_ID))
                    .doesNotThrowAnyException();

            verify(valueOperations).get(accessKey);
            verify(valueOperations).get(refreshKey);
            verify(valueOperations, times(2)).set(anyString(), eq("1"), any(Duration.class));
            verify(redisTemplate).delete(accessKey);
            verify(redisTemplate).delete(refreshKey);
            verify(zSetOperations).remove("user:online:" + UID, DEVICE_ID);
            verify(hashOperations).delete("user:devices:" + UID, DEVICE_ID);
            verify(userFeignClient).markOffline(eq(UID), any());
        }

        @Test
        @DisplayName("should not blacklist when tokens are already removed from Redis")
        void shouldNotBlacklistWhenTokensAlreadyRemoved() {
            String accessKey = "token:access:" + UID + ":" + DEVICE_ID;
            String refreshKey = "token:refresh:" + UID + ":" + DEVICE_ID;
            when(valueOperations.get(accessKey)).thenReturn(null);
            when(valueOperations.get(refreshKey)).thenReturn(null);
            when(redisTemplate.delete(anyString())).thenReturn(true);
            when(zSetOperations.remove(anyString(), any())).thenReturn(0L);
            when(hashOperations.delete(anyString(), anyString())).thenReturn(0L);

            assertThatCode(() -> authService.kickDevice(UID, DEVICE_ID))
                    .doesNotThrowAnyException();

            verify(valueOperations, never()).set(anyString(), eq("1"), any(Duration.class));
            verify(redisTemplate).delete(accessKey);
            verify(redisTemplate).delete(refreshKey);
            verify(userFeignClient).markOffline(eq(UID), any());
        }

        @Test
        @DisplayName("should not throw when markOffline fails during kick")
        void shouldNotThrowWhenMarkOfflineFailsDuringKick() {
            String accessKey = "token:access:" + UID + ":" + DEVICE_ID;
            when(valueOperations.get(accessKey)).thenReturn(null);
            when(valueOperations.get(anyString())).thenReturn(null);
            when(redisTemplate.delete(anyString())).thenReturn(true);
            when(zSetOperations.remove(anyString(), any())).thenReturn(0L);
            when(hashOperations.delete(anyString(), anyString())).thenReturn(0L);
            doThrow(new RuntimeException("Network error")).when(userFeignClient).markOffline(eq(1L), any());

            assertThatCode(() -> authService.kickDevice(UID, DEVICE_ID))
                    .doesNotThrowAnyException();

            verify(userFeignClient).markOffline(eq(UID), any());
        }
    }
}

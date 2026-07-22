package me.aster.echochat.user.service.impl;

import me.aster.echochat.common.entity.User;
import me.aster.echochat.common.exception.BusinessException;
import me.aster.echochat.common.util.SnowflakeIdGenerator;
import me.aster.echochat.user.mapper.UserMapper;
import me.aster.echochat.user.mq.UserIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private SnowflakeIdGenerator idGenerator;
    @Mock private UserIndexService userIndexService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UserServiceImpl userService;



    @Nested
    @DisplayName("findByAccount")
    class FindByAccount {

        @Test
        @DisplayName("should find user by account")
        void shouldFindByAccount() {
            User u = new User();
            u.setUid(100L);
            u.setNickname("Test");
            when(userMapper.findByAccount("100")).thenReturn(u);

            User result = userService.findByAccount("100");
            assertThat(result).isSameAs(u);
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenNotFound() {
            when(userMapper.findByAccount("999")).thenReturn(null);

            assertThatThrownBy(() -> userService.findByAccount("999"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("getUserByUid")
    class GetUserByUid {

        @Test
        @DisplayName("should return user when exists")
        void shouldReturnUser() {
            User u = new User();
            u.setUid(100L);
            when(userMapper.selectById(100L)).thenReturn(u);

            assertThat(userService.getUserByUid(100L)).isSameAs(u);
        }

        @Test
        @DisplayName("should throw when user does not exist")
        void shouldThrowWhenNotExist() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.getUserByUid(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("getUsersByUids")
    class GetUsersByUids {

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyForEmptyInput() {
            assertThat(userService.getUsersByUids(List.of())).isEmpty();
        }

        @Test
        @DisplayName("should batch fetch users")
        void shouldBatchFetch() {
            User u1 = new User();
            u1.setUid(1L);
            User u2 = new User();
            u2.setUid(2L);
            when(userMapper.selectBatchIds(anyList())).thenReturn(List.of(u1, u2));

            assertThat(userService.getUsersByUids(List.of(1L, 2L))).hasSize(2);
        }
    }
}

package me.aster.echochat.common;

import me.aster.echochat.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResultCode")
class ResultCodeTest {

    @Nested
    @DisplayName("code ranges")
    class CodeRanges {

        @Test
        @DisplayName("SUCCESS should be 200")
        void successShouldBe200() {
            assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
            assertThat(ResultCode.SUCCESS.getMessage()).isEqualTo("success");
        }

        @Test
        @DisplayName("user codes should be in 1000-1999 range")
        void userCodesShouldBeInRange() {
            assertThat(ResultCode.USER_NOT_FOUND.getCode()).isEqualTo(1001);
            assertThat(ResultCode.PASSWORD_ERROR.getCode()).isEqualTo(1003);
            assertThat(ResultCode.TOKEN_EXPIRED.getCode()).isEqualTo(1004);
        }

        @Test
        @DisplayName("message codes should be in 2000-2999 range")
        void messageCodesShouldBeInRange() {
            assertThat(ResultCode.MESSAGE_NOT_FOUND.getCode()).isEqualTo(2001);
            assertThat(ResultCode.MESSAGE_RECALL_TIME_EXCEEDED.getCode()).isEqualTo(2002);
        }

        @Test
        @DisplayName("group codes should be in 3000-3999 range")
        void groupCodesShouldBeInRange() {
            assertThat(ResultCode.GROUP_NOT_FOUND.getCode()).isEqualTo(3001);
            assertThat(ResultCode.NOT_GROUP_MEMBER.getCode()).isEqualTo(3003);
        }

        @Test
        @DisplayName("moment codes should be in 4000-4999 range")
        void momentCodesShouldBeInRange() {
            assertThat(ResultCode.MOMENT_NOT_FOUND.getCode()).isEqualTo(4001);
            assertThat(ResultCode.MOMENT_PERMISSION_DENIED.getCode()).isEqualTo(4002);
        }

        @Test
        @DisplayName("notification codes should be in 5000-5999 range")
        void notificationCodesShouldBeInRange() {
            assertThat(ResultCode.NOTIFICATION_NOT_FOUND.getCode()).isEqualTo(5001);
            assertThat(ResultCode.NOTIFICATION_PUSH_FAILED.getCode()).isEqualTo(5003);
        }
    }

    @Nested
    @DisplayName("HTTP status codes")
    class HttpStatusCodes {

        @Test
        @DisplayName("should have standard HTTP error codes")
        void shouldHaveStandardHttpCodes() {
            assertThat(ResultCode.BAD_REQUEST.getCode()).isEqualTo(400);
            assertThat(ResultCode.UNAUTHORIZED.getCode()).isEqualTo(401);
            assertThat(ResultCode.FORBIDDEN.getCode()).isEqualTo(403);
            assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(404);
            assertThat(ResultCode.INTERNAL_ERROR.getCode()).isEqualTo(500);
        }
    }
}

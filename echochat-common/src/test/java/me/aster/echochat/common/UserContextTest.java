package me.aster.echochat.common;

import me.aster.echochat.common.context.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserContext")
class UserContextTest {

    @Nested
    @DisplayName("basic operations")
    class BasicOperations {

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("should return the value that was set")
        void shouldReturnSetValue() {
            try {
                UserContext.set(100L);
                assertThat(UserContext.get()).isEqualTo(100L);
            } finally {
                UserContext.clear();
            }
        }

        @Test
        @DisplayName("should return null after clear")
        void shouldReturnNullAfterClear() {
            UserContext.set(200L);
            UserContext.clear();
            assertThat(UserContext.get()).isNull();
        }

        @Test
        @DisplayName("should support multiple sets on the same thread")
        void shouldSupportMultipleSets() {
            try {
                UserContext.set(1L);
                assertThat(UserContext.get()).isEqualTo(1L);
                UserContext.set(2L);
                assertThat(UserContext.get()).isEqualTo(2L);
            } finally {
                UserContext.clear();
            }
        }
    }

    @Nested
    @DisplayName("thread isolation")
    class ThreadIsolation {

        @Test
        @DisplayName("should isolate values between threads")
        void shouldIsolateValuesBetweenThreads() throws Exception {
            UserContext.set(1L);
            Long[] otherValue = new Long[1];
            Thread t = new Thread(() -> {
                otherValue[0] = UserContext.get();
            });
            t.start();
            t.join();
            assertThat(UserContext.get()).isEqualTo(1L);
            assertThat(otherValue[0]).isNull();
            UserContext.clear();
        }
    }
}

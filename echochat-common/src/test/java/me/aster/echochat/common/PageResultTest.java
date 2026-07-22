package me.aster.echochat.common;

import me.aster.echochat.common.dto.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageResult")
class PageResultTest {

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("should calculate pages correctly")
        void shouldCalculatePages() {
            PageResult<String> result = PageResult.of(1, 10, 25, List.of("a", "b", "c"));
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotal()).isEqualTo(25);
            assertThat(result.getPages()).isEqualTo(3);
            assertThat(result.getRecords()).hasSize(3);
        }

        @Test
        @DisplayName("should handle exact division")
        void shouldHandleExactDivision() {
            PageResult<String> result = PageResult.of(1, 10, 30, List.of());
            assertThat(result.getPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("should handle zero total")
        void shouldHandleZeroTotal() {
            PageResult<String> result = PageResult.of(1, 10, 0, List.of());
            assertThat(result.getPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle single page")
        void shouldHandleSinglePage() {
            PageResult<String> result = PageResult.of(1, 10, 5, List.of("x"));
            assertThat(result.getPages()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("empty")
    class Empty {

        @Test
        @DisplayName("should return zero total and pages")
        void shouldReturnZeroTotals() {
            PageResult<String> result = PageResult.empty(2, 20);
            assertThat(result.getPage()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(20);
            assertThat(result.getTotal()).isEqualTo(0);
            assertThat(result.getPages()).isEqualTo(0);
            assertThat(result.getRecords()).isEmpty();
        }
    }
}

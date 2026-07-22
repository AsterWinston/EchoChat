package me.aster.echochat.common;

import me.aster.echochat.common.dto.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageRequest")
class PageRequestTest {

    @Nested
    @DisplayName("getOffset")
    class GetOffset {

        @Test
        @DisplayName("should return 0 for first page with default size")
        void shouldReturnZeroForFirstPage() {
            PageRequest req = new PageRequest();
            assertThat(req.getPage()).isEqualTo(1);
            assertThat(req.getSize()).isEqualTo(20);
            assertThat(req.getOffset()).isEqualTo(0);
        }

        @Test
        @DisplayName("should calculate correct offset for non-first page")
        void shouldCalculateOffsetForNonFirstPage() {
            PageRequest req = new PageRequest(3, 10);
            assertThat(req.getOffset()).isEqualTo(20);
        }

        @Test
        @DisplayName("should return negative offset for page 0")
        void shouldHandlePageZero() {
            PageRequest req = new PageRequest(0, 10);
            assertThat(req.getOffset()).isEqualTo(-10);
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("should have default page=1 size=20")
        void shouldHaveDefaults() {
            PageRequest req = new PageRequest();
            assertThat(req.getPage()).isEqualTo(1);
            assertThat(req.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("should accept custom page and size")
        void shouldAcceptCustomValues() {
            PageRequest req = new PageRequest(5, 50);
            assertThat(req.getPage()).isEqualTo(5);
            assertThat(req.getSize()).isEqualTo(50);
        }
    }
}

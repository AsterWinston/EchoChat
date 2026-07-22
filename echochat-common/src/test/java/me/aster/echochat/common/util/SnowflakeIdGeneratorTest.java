package me.aster.echochat.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SnowflakeIdGenerator} verifying unique ID
 * generation, monotonic ordering, concurrency safety, and constructor
 * argument validation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SnowflakeIdGenerator")
class SnowflakeIdGeneratorTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should create generator with valid worker and datacenter IDs")
        void shouldCreateWithValidIds() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0);
            assertThat(generator.nextId()).isPositive();
        }

        @Test
        @DisplayName("should create generator with maximum allowed worker and datacenter IDs")
        void shouldCreateWithMaxAllowedIds() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(31, 31);
            assertThat(generator.nextId()).isPositive();
        }

        @Test
        @DisplayName("should throw when workerId is negative")
        void shouldThrowWhenWorkerIdIsNegative() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("should throw when workerId exceeds maximum")
        void shouldThrowWhenWorkerIdExceedsMax() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("should throw when datacenterId is negative")
        void shouldThrowWhenDatacenterIdIsNegative() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(0, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("datacenterId");
        }

        @Test
        @DisplayName("should throw when datacenterId exceeds maximum")
        void shouldThrowWhenDatacenterIdExceedsMax() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(0, 32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("datacenterId");
        }
    }

    @Nested
    @DisplayName("nextId")
    class NextId {

        private SnowflakeIdGenerator generator;

        @BeforeEach
        void setUp() {
            generator = new SnowflakeIdGenerator(1, 5);
        }

        @Test
        @DisplayName("should generate positive IDs")
        void shouldGeneratePositiveIds() {
            for (int i = 0; i < 100; i++) {
                assertThat(generator.nextId()).isPositive();
            }
        }

        @Test
        @DisplayName("should generate unique IDs on repeated calls")
        void shouldGenerateUniqueIds() {
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 10_000; i++) {
                long id = generator.nextId();
                assertThat(ids.add(id))
                        .as("Duplicate ID generated: %d after %d calls", id, i + 1)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should generate monotonically increasing IDs within single thread")
        void shouldGenerateMonotonicallyIncreasingIds() {
            long previous = generator.nextId();
            for (int i = 0; i < 10_000; i++) {
                long current = generator.nextId();
                assertThat(current).isGreaterThan(previous);
                previous = current;
            }
        }

        @Test
        @DisplayName("should generate unique IDs under concurrent access")
        void shouldGenerateUniqueIdsUnderConcurrentAccess() throws InterruptedException {
            SnowflakeIdGenerator shared = new SnowflakeIdGenerator(1, 5);
            int threadCount = 10;
            int idsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            Set<Long> allIds = ConcurrentHashMap.newKeySet();

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < idsPerThread; i++) {
                            allIds.add(shared.nextId());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            int expectedTotal = threadCount * idsPerThread;
            assertThat(allIds).hasSize(expectedTotal);
        }

        @Test
        @DisplayName("should generate distinct IDs across different generator instances")
        void shouldGenerateDistinctIdsAcrossDifferentInstances() {
            SnowflakeIdGenerator gen1 = new SnowflakeIdGenerator(1, 5);
            SnowflakeIdGenerator gen2 = new SnowflakeIdGenerator(2, 5);
            SnowflakeIdGenerator gen3 = new SnowflakeIdGenerator(1, 6);

            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(gen1.nextId());
                ids.add(gen2.nextId());
                ids.add(gen3.nextId());
            }

            assertThat(ids).hasSize(300);
        }

        @Test
        @DisplayName("should handle rapid sequence exhaustion within same millisecond")
        void shouldHandleRapidSequenceExhaustion() {
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 5_000; i++) {
                long id = generator.nextId();
                assertThat(ids.add(id)).isTrue();
            }
            assertThat(ids).hasSize(5_000);
        }
    }
}

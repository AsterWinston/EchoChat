package me.aster.echochat.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IpBlacklistFilter")
class IpBlacklistFilterTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private ReactiveSetOperations<String, String> setOps;
    @Mock private ServerWebExchange exchange;
    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private GatewayFilterChain chain;
    @Mock private InetSocketAddress remoteAddress;
    @Mock private InetAddress inetAddress;

    @InjectMocks
    private IpBlacklistFilter ipBlacklistFilter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(response.setComplete()).thenReturn(Mono.empty());
        lenient().when(chain.filter(exchange)).thenReturn(Mono.empty());
        lenient().when(request.getRemoteAddress()).thenReturn(remoteAddress);
        lenient().when(remoteAddress.getAddress()).thenReturn(inetAddress);
    }

    @Test
    @DisplayName("should return order -200")
    void shouldReturnOrder() {
        assertThat(ipBlacklistFilter.getOrder()).isEqualTo(-200);
    }

    @Nested
    @DisplayName("blocking blacklisted IP")
    class BlockingBlacklistedIp {

        @BeforeEach
        void setUp() {
            when(setOps.isMember(eq("ip:blacklist"), eq("192.168.1.100")))
                    .thenReturn(Mono.just(true));
            when(inetAddress.isLoopbackAddress()).thenReturn(false);
            when(inetAddress.getHostAddress()).thenReturn("192.168.1.100");
        }

        @Test
        @DisplayName("should return 403 when IP is in blacklist")
        void shouldReturn403ForBlacklistedIp() {
            ipBlacklistFilter.filter(exchange, chain).block();

            verify(response).setStatusCode(HttpStatus.FORBIDDEN);
            verify(response).setComplete();
            verify(chain, never()).filter(any());
        }
    }

    @Nested
    @DisplayName("allowing non-blacklisted IP")
    class AllowingNonBlacklistedIp {

        @BeforeEach
        void setUp() {
            when(setOps.isMember(eq("ip:blacklist"), eq("10.0.0.1")))
                    .thenReturn(Mono.just(false));
            when(inetAddress.isLoopbackAddress()).thenReturn(false);
            when(inetAddress.getHostAddress()).thenReturn("10.0.0.1");
        }

        @Test
        @DisplayName("should delegate to next filter when IP is not blacklisted")
        void shouldDelegateToNextFilter() {
            ipBlacklistFilter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }
    }

    @Nested
    @DisplayName("Redis failure handling")
    class RedisFailure {

        @BeforeEach
        void setUp() {
            when(inetAddress.isLoopbackAddress()).thenReturn(false);
            when(inetAddress.getHostAddress()).thenReturn("10.0.0.2");
        }

        @Test
        @DisplayName("should fail open and allow request when Redis throws exception")
        void shouldFailOpenOnRedisException() {
            when(setOps.isMember(eq("ip:blacklist"), eq("10.0.0.2")))
                    .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("should fail open and allow request when Redis times out")
        void shouldFailOpenOnRedisTimeout() {
            when(setOps.isMember(eq("ip:blacklist"), eq("10.0.0.2")))
                    .thenReturn(Mono.<Boolean>never().timeout(Duration.ofMillis(1)));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }
    }

    @Nested
    @DisplayName("IP normalization")
    class IpNormalization {

        @Test
        @DisplayName("should normalize IPv6 loopback ::1 to 127.0.0.1")
        void shouldNormalizeIPv6Loopback() {
            when(inetAddress.isLoopbackAddress()).thenReturn(true);
            when(setOps.isMember(eq("ip:blacklist"), eq("127.0.0.1")))
                    .thenReturn(Mono.just(false));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(setOps).isMember(eq("ip:blacklist"), eq("127.0.0.1"));
        }

        @Test
        @DisplayName("should strip IPv4-mapped prefix from ::ffff:x.x.x.x")
        void shouldStripIPv4MappedPrefix() {
            when(inetAddress.isLoopbackAddress()).thenReturn(false);
            when(inetAddress.getHostAddress()).thenReturn("::ffff:172.16.0.5");
            when(setOps.isMember(eq("ip:blacklist"), eq("172.16.0.5")))
                    .thenReturn(Mono.just(false));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(setOps).isMember(eq("ip:blacklist"), eq("172.16.0.5"));
        }

        @Test
        @DisplayName("should pass plain IPv4 address unchanged")
        void shouldPassPlainIPv4Unchanged() {
            when(inetAddress.isLoopbackAddress()).thenReturn(false);
            when(inetAddress.getHostAddress()).thenReturn("8.8.8.8");
            when(setOps.isMember(eq("ip:blacklist"), eq("8.8.8.8")))
                    .thenReturn(Mono.just(false));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(setOps).isMember(eq("ip:blacklist"), eq("8.8.8.8"));
        }

        @Test
        @DisplayName("should return unknown when remote address is null")
        void shouldReturnUnknownForNullRemote() {
            when(request.getRemoteAddress()).thenReturn(null);
            when(setOps.isMember(eq("ip:blacklist"), eq("unknown")))
                    .thenReturn(Mono.just(false));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(setOps).isMember(eq("ip:blacklist"), eq("unknown"));
        }

        @Test
        @DisplayName("should return unknown when IP address is null")
        void shouldReturnUnknownForNullAddress() {
            when(remoteAddress.getAddress()).thenReturn(null);
            when(setOps.isMember(eq("ip:blacklist"), eq("unknown")))
                    .thenReturn(Mono.just(false));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(setOps).isMember(eq("ip:blacklist"), eq("unknown"));
        }

        @Test
        @DisplayName("should return unknown when host address is null")
        void shouldReturnUnknownForNullHostAddress() {
            when(inetAddress.isLoopbackAddress()).thenReturn(false);
            when(inetAddress.getHostAddress()).thenReturn(null);
            when(setOps.isMember(eq("ip:blacklist"), eq("unknown")))
                    .thenReturn(Mono.just(false));

            ipBlacklistFilter.filter(exchange, chain).block();

            verify(setOps).isMember(eq("ip:blacklist"), eq("unknown"));
        }
    }
}

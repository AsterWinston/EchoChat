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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssFilter")
class XssFilterTest {

    @Mock private ServerWebExchange exchange;
    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private GatewayFilterChain chain;
    @Mock private URI uri;
    @Mock private HttpHeaders headers;

    @InjectMocks
    private XssFilter xssFilter;

    @BeforeEach
    void setUp() {
        lenient().when(exchange.getRequest()).thenReturn(request);
        lenient().when(request.getURI()).thenReturn(uri);
        lenient().when(exchange.getResponse()).thenReturn(response);
        lenient().when(response.setComplete()).thenReturn(Mono.empty());
        lenient().when(request.getHeaders()).thenReturn(headers);
        lenient().when(headers.getFirst("Referer")).thenReturn("");
        lenient().when(headers.getFirst("User-Agent")).thenReturn("");
        lenient().when(chain.filter(exchange)).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("should return order -250")
    void shouldReturnOrder() {
        assertThat(xssFilter.getOrder()).isEqualTo(-250);
    }

    @Nested
    @DisplayName("blocking XSS patterns")
    class BlockingXss {

        @BeforeEach
        void setUp() {
            when(uri.getPath()).thenReturn("/api/test");
        }

        @Test
        @DisplayName("should block script tag in query parameters")
        void shouldBlockScriptTagInQuery() {
            when(uri.getRawQuery()).thenReturn("name=<script>alert(1)</script>");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("should block javascript: pseudo-protocol in query")
        void shouldBlockJavascriptProtocol() {
            when(uri.getRawQuery()).thenReturn("redirect=javascript:alert(1)");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block onerror attribute in query")
        void shouldBlockOnerrorAttribute() {
            when(uri.getRawQuery()).thenReturn("src=x onerror=alert(1)");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block iframe tag in query")
        void shouldBlockIframeTag() {
            when(uri.getRawQuery()).thenReturn("content=<iframe src=evil>");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block XSS pattern in request path")
        void shouldBlockXssInPath() {
            when(uri.getPath()).thenReturn("/api/<script>evil");
            when(uri.getRawQuery()).thenReturn("foo=bar");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block XSS in Referer header")
        void shouldBlockXssInRefererHeader() {
            when(uri.getRawQuery()).thenReturn("name=test");
            when(headers.getFirst("Referer")).thenReturn("https://evil.com/<script>steal");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block XSS in User-Agent header")
        void shouldBlockXssInUserAgent() {
            when(uri.getRawQuery()).thenReturn("name=test");
            when(headers.getFirst("User-Agent")).thenReturn("onload=eval(atob('x'))");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }
    }

    @Nested
    @DisplayName("blocking SQL injection")
    class BlockingSql {

        @BeforeEach
        void setUp() {
            when(uri.getPath()).thenReturn("/api/users");
        }

        @Test
        @DisplayName("should block union select in query")
        void shouldBlockUnionSelect() {
            when(uri.getRawQuery()).thenReturn("id=1 union select password from users");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block drop table in query")
        void shouldBlockDropTable() {
            when(uri.getRawQuery()).thenReturn("id=1; drop table users");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }

        @Test
        @DisplayName("should block SQL comment termination in query")
        void shouldBlockSqlCommentTermination() {
            when(uri.getRawQuery()).thenReturn("user=admin'--");

            xssFilter.filter(exchange, chain);

            verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
            verify(response).setComplete();
        }
    }

    @Nested
    @DisplayName("allowing safe requests")
    class AllowingSafe {

        @BeforeEach
        void setUp() {
            when(uri.getPath()).thenReturn("/api/test");
        }

        @Test
        @DisplayName("should allow normal text parameters")
        void shouldAllowNormalText() {
            when(uri.getPath()).thenReturn("/api/users");
            when(uri.getRawQuery()).thenReturn("name=John&age=25&city=NYC");

            xssFilter.filter(exchange, chain);

            verify(chain).filter(exchange);
            verify(response, never()).setStatusCode(any());
        }

        @Test
        @DisplayName("should allow plain text without html tags")
        void shouldAllowPlainText() {
            when(uri.getPath()).thenReturn("/api/messages");
            when(uri.getRawQuery()).thenReturn("content=Hello World");

            xssFilter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("should allow request with null query string")
        void shouldAllowNullQuery() {
            when(uri.getRawQuery()).thenReturn(null);

            xssFilter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("should allow request with empty query string")
        void shouldAllowEmptyQuery() {
            when(uri.getRawQuery()).thenReturn("");

            xssFilter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("should allow URL-encoded harmless content")
        void shouldAllowUrlEncodedContent() {
            when(uri.getRawQuery()).thenReturn("q=hello%20world&page=1");

            xssFilter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }

        @Test
        @DisplayName("should allow safe path with special chars")
        void shouldAllowSafePath() {
            when(uri.getPath()).thenReturn("/api/users/123/posts/456");

            xssFilter.filter(exchange, chain);

            verify(chain).filter(exchange);
        }
    }
}

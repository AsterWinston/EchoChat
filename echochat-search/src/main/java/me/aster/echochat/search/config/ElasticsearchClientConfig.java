package me.aster.echochat.search.config;

import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 限制Elasticsearch连接的HTTP Keep-Alive时长，使空闲Socket在云NAT/LB超时重置之前被回收。
 * 注册为{@link RestClientBuilder.HttpClientConfigCallback} Bean，Spring Boot的Elasticsearch自动配置会
 * 自动获取并应用到RestClient构建器。
 * @author AsterWinston
 */
@Configuration
public class ElasticsearchClientConfig {

    /** Keep-Alive连接的最大空闲秒数（默认45秒，低于常见的NAT 60秒超时）。 */
    @Value("${spring.elasticsearch.keep-alive-seconds:45}")
    private long keepAliveSeconds;

    @Bean
    RestClientBuilder.HttpClientConfigCallback esHttpClientConfigCallback() {
        return new RestClientBuilder.HttpClientConfigCallback() {
            @Override
            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                return httpClientBuilder.setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
                    @Override
                    public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                        HeaderElementIterator it = new BasicHeaderElementIterator(
                                response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                        while (it.hasNext()) {
                            HeaderElement he = it.nextElement();
                            String param = he.getName();
                            String value = he.getValue();
                            if (value != null && "timeout".equalsIgnoreCase(param)) {
                                try {
                                    return Math.min(Long.parseLong(value) * 1000,
                                            keepAliveSeconds * 1000L);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                        return keepAliveSeconds * 1000L;
                    }
                });
            }
        };
    }
}

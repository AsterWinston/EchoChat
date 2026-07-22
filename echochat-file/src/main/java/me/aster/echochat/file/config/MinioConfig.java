package me.aster.echochat.file.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

/**
 * MinIO对象存储客户端配置，包含连接超时设置。
 * @author AsterWinston
 */
@Configuration
public class MinioConfig {

    /** MinIO服务器端点URL */
    @Value("${minio.endpoint}")
    private String endpoint;

    /** MinIO访问密钥 */
    @Value("${minio.access-key}")
    private String accessKey;

    /** MinIO秘密密钥 */
    @Value("${minio.secret-key}")
    private String secretKey;

    /**
     * @return 已配置连接/读/写超时的 {@link MinioClient} bean
     */
    @Bean
    public MinioClient minioClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }
}

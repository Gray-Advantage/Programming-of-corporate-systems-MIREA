package space.grayt.teremok.renderer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import space.grayt.teremok.storage.MinioObjectStorage;
import space.grayt.teremok.storage.ObjectStorage;

@Configuration
public class RendererConfiguration {

    @Bean
    ObjectStorage objectStorage(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            @Value("${storage.bucket}") String bucket) {
        return new MinioObjectStorage(endpoint, accessKey, secretKey, bucket);
    }
}

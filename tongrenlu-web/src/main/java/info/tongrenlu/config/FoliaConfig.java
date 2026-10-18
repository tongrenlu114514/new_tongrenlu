package info.tongrenlu.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class FoliaConfig {

    @Value("${folia.base-url:http://121.37.183.44/folia/}")
    private String baseUrl;

    public String albumUrl(Long albumId) {
        return baseUrl + "#collection/online/tongrenlu/album/" + albumId;
    }
}

package hongik.finEdu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finEduOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("finEdu API")
                        .description("금융 교육·뉴스·퀴즈·캘린더·포인트 교환 등 백엔드 REST API")
                        .version("v0.0.1"));
    }
}

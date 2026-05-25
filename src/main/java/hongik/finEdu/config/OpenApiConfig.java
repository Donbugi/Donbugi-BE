package hongik.finEdu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI finEduOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("finEdu API (돈부기 백엔드)")
                        .version("v0.0.1"))
                .tags(List.of(
                        tag(OpenApiTags.AUTH, "회원가입, 로그인, 닉네임, 온보딩(캐릭터 레벨)"),
                        tag(OpenApiTags.MAIN, "코스피, 경제 날씨(시장 심리)"),
                        tag(OpenApiTags.NEWS, "뉴스 피드, 검색, 관심 토픽"),
                        tag(OpenApiTags.QUIZ, "퀴즈 생성·제출, 정답률·오답노트"),
                        tag(OpenApiTags.POINTS, "잔액, 적립, 교환, 출석, 월간 요약"),
                        tag(OpenApiTags.CALENDAR, "세무·이슈·사용자 일정, 통합 캘린더"),
                        tag(OpenApiTags.NOTIFICATION, "앱 내 알림 목록 (DB 저장)"),
                        tag(OpenApiTags.TRADING, "모의투자 20종목, 주문, 체결, 계좌")
                ))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("signup/login 응답 accessToken")));
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}

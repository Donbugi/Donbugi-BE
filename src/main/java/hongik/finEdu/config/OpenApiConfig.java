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
                        .version("v0.0.1")
                        .description("""
                                ## 개요
                                금융 교육 앱 **돈부기** 백엔드 REST API입니다.
                                
                                ## 인증
                                1. `POST /api/auth/signup` 또는 `POST /api/auth/login` 호출
                                2. 응답의 `accessToken`을 복사
                                3. Swagger 우측 상단 **Authorize** → `Bearer {accessToken}` 입력
                                   (또는 HTTP 헤더 `Authorization: Bearer {accessToken}`)
                                4. 이후 🔒 표시 API는 JWT 필수
                                
                                ## userId 규칙
                                - 대부분 API는 JWT의 userId를 사용합니다.
                                - `userId` 쿼리/바디를 **생략**하면 토큰 userId가 적용됩니다.
                                - **다른 userId**를 넣으면 403 (FORBIDDEN)입니다.
                                
                                ## 포인트 정책 (요약)
                                | 활동 | 포인트 |
                                |------|--------|
                                | 오늘의 과제 1문항 참여 | +20P |
                                | 오늘의 과제 3문항 전부 정답 | +20P 보너스 |
                                | 뉴스 5·10·15…회 읽기 | +20P (5회마다) |
                                | 뉴스 상세 퀴즈 참여 (기사당 1회) | +20P |
                                | 연속 출석 7·14·21…일 | +100P |
                                
                                ## 알림
                                - 푸시/이메일 발송 없음. `GET /api/notifications` 최초 호출 시 DB seed.
                                - 오늘 금융 일정(최대 3건) + 오늘의 과제 알림이 생성됩니다.
                                """))
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

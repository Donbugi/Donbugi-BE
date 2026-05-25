package hongik.finEdu.config;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Swagger 파라미터 설명 재사용. */
public final class OpenApiParams {

    private OpenApiParams() {}

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Parameter(
            name = "userId",
            in = ParameterIn.QUERY,
            description = """
                    사용자 UUID (signup/login 응답의 userId).
                    생략 시 JWT 토큰의 userId 사용.
                    값을 넣으면 토큰 userId와 일치해야 함.""",
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    public @interface UserIdQuery {}

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Parameter(
            name = "year",
            in = ParameterIn.QUERY,
            description = "연도 (4자리)",
            example = "2026"
    )
    public @interface YearQuery {}

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Parameter(
            name = "month",
            in = ParameterIn.QUERY,
            description = "월 (1~12). year와 함께 지정하거나 둘 다 생략(이번 달).",
            example = "5"
    )
    public @interface MonthQuery {}
}

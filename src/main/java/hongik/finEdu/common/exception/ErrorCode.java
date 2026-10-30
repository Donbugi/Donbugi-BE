package hongik.finEdu.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ──
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "서버 내부 오류가 발생했습니다", false),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C002", "잘못된 입력입니다", false),
    CONFLICT(HttpStatus.CONFLICT, "C003", "동시에 처리 중인 요청과 충돌했습니다. 잠시 후 다시 시도해 주세요", false),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "C004", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요", false),
    ENDPOINT_GONE(HttpStatus.GONE, "C005", "더 이상 지원하지 않는 API입니다", false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "요청한 경로를 찾을 수 없습니다", false),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C007", "지원하지 않는 HTTP 메서드입니다", false),

    // ── 기사(Article) ──
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "기사를 찾을 수 없습니다", false),
    NO_ARTICLES_FOR_QUIZ(HttpStatus.NOT_FOUND, "A002", "본문이 있는 기사가 없어 퀴즈를 만들 수 없습니다", false),

    // ── 퀴즈(Quiz) ──
    QUIZ_GENERATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Q001", "퀴즈 생성 시간이 초과되었습니다", false),
    QUIZ_ALREADY_ANSWERED(HttpStatus.CONFLICT, "Q002", "이미 답안을 제출한 문항입니다", false),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "Q003", "퀴즈 문항을 찾을 수 없습니다", false),

    // ── AI 서버 (일시적 장애 → 재시도 대상) ──
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI001", "AI 서버 호출에 실패했습니다", true),

    // ── AI 서버 (영구적 오류 → 재시도 불가) ──
    AI_RESPONSE_PARSE_ERROR(HttpStatus.BAD_GATEWAY, "AI002", "AI 서버 응답을 파싱할 수 없습니다", false),

    // ── 세무일정(Calendar) ──
    CRAWL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "T001", "세무일정 크롤링에 실패했습니다", false),

    // ── 시세(Main) ──
    KOSPI_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "M001", "코스피 지수를 가져오지 못했습니다", false),
    ECONOMIC_WEATHER_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "M002", "경제 날씨 데이터를 가져오지 못했습니다", false),

    // ── 포인트 교환 ──
    POINT_BENEFIT_NOT_FOUND(HttpStatus.BAD_REQUEST, "P001", "알 수 없는 혜택 코드입니다", false),
    INSUFFICIENT_POINTS(HttpStatus.BAD_REQUEST, "P002", "포인트가 부족합니다", false),

    // ── 인증(Auth) ──
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U001", "이미 가입된 이메일입니다", false),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "U002", "이메일 또는 비밀번호가 올바르지 않습니다", false),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "U003", "로그인이 필요합니다", false),
    FORBIDDEN(HttpStatus.FORBIDDEN, "U004", "접근 권한이 없습니다", false),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "U005", "온보딩이 이미 완료되었습니다", false),
    ONBOARDING_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "U006", "온보딩을 먼저 완료해 주세요", false),
    CALENDAR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CAL001", "일정을 찾을 수 없습니다", false),

    // ── 모의투자(Trading) ──
    INSUFFICIENT_CASH(HttpStatus.BAD_REQUEST, "TR001", "잔고가 부족합니다", false),
    INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "TR002", "보유 수량이 부족합니다", false),
    INVALID_STOCK_CODE(HttpStatus.BAD_REQUEST, "TR003", "유효하지 않은 종목 코드입니다", false),
    MARKET_CLOSED(HttpStatus.BAD_REQUEST, "TR004", "주문 가능 시간이 아닙니다 (영업일 08:30~15:30)", false),
    INVALID_LIMIT_PRICE(HttpStatus.BAD_REQUEST, "TR005", "지정가가 현재가와 동일합니다. 시장가 주문을 이용해 주세요", false),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "TR006", "주문을 찾을 수 없습니다", false),
    ORDER_ALREADY_DONE(HttpStatus.BAD_REQUEST, "TR007", "이미 체결된 주문은 취소할 수 없습니다", false),
    STOCK_PRICE_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "TR008", "종목 시세를 가져오지 못했습니다", false),
    INVALID_TICK_SIZE(HttpStatus.BAD_REQUEST, "TR009", "호가가격단위에 맞지 않는 가격입니다", false),
    PRICE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "TR010", "가격제한폭(상·하한가)을 벗어난 가격입니다", false),
    STALE_PRICE(HttpStatus.SERVICE_UNAVAILABLE, "TR011", "시세가 지연되어 시장가 주문을 받을 수 없습니다. 잠시 후 다시 시도해 주세요", false),
    NO_LIQUIDITY(HttpStatus.CONFLICT, "TR012", "체결 가능한 호가 잔량이 없습니다", false),
    ORDER_NOT_MODIFIABLE(HttpStatus.BAD_REQUEST, "TR013", "정정할 수 없는 주문입니다", false);

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
    private final boolean retryable;
}

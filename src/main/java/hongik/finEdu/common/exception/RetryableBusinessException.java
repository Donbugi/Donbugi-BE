package hongik.finEdu.common.exception;

/**
 * 재시도 가능한 일시적 장애 (네트워크 타임아웃, 5xx 등)
 * {@link org.springframework.resilience.annotation.Retryable}이 이 타입만 재시도함
 */
public class RetryableBusinessException extends BusinessException {

    public RetryableBusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RetryableBusinessException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public RetryableBusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}

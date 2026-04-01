package hongik.finEdu.main.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class KospiResponseDto {

    private final BigDecimal value;
    private final String formatted;
    private final boolean cached;
    private final Instant at;
}

package hongik.finEdu.main;

import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.main.dto.KospiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = OpenApiTags.MAIN)
@RestController
@RequestMapping("/api/main")
@RequiredArgsConstructor
public class KospiController {

    private final KospiService kospiService;

    @Operation(
            summary = "코스피 지수",
            description = "네이버 금융 스크랩 + Redis 캐시(약 60초). 인증 불필요.")
    @GetMapping("/kospi")
    public ResponseEntity<KospiResponseDto> getKospi() {
        return ResponseEntity.ok(kospiService.getKospi());
    }
}

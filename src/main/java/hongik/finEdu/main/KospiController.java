package hongik.finEdu.main;

import hongik.finEdu.main.dto.KospiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/main")
@RequiredArgsConstructor
public class KospiController {

    private final KospiService kospiService;

    /**
     * 코스피 지수 (네이버 금융, Redis 캐시)
     * GET /api/main/kospi
     */
    @GetMapping("/kospi")
    public ResponseEntity<KospiResponseDto> getKospi() {
        return ResponseEntity.ok(kospiService.getKospi());
    }
}

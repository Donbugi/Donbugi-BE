package hongik.finEdu.calendar.service;

import hongik.finEdu.calendar.entity.TaxSchedule;
import hongik.finEdu.calendar.repository.TaxScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxScheduleCrawler {

    private static final String BASE_URL =
        "https://www.nts.go.kr/nts/ad/taxSchdul/selectList.do";
    private static final String MI_PARAM = "135747";
    private static final int REQUEST_DELAY_MS = 1500;  // 요청 간 1.5초 딜레이
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    private final TaxScheduleRepository taxScheduleRepository;

    /**
     * 현재 월 기준 3개월치 크롤링 (현재월 + 다음달 + 다다음달)
     */
    @Transactional
    public void crawlNextThreeMonths() {
        LocalDate now = LocalDate.now();

        for (int i = 0; i < 3; i++) {
            YearMonth targetMonth = YearMonth.from(now).plusMonths(i);
            try {
                crawlMonth(targetMonth.getYear(), targetMonth.getMonthValue());
                Thread.sleep(REQUEST_DELAY_MS); // 요청 간 딜레이
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("크롤링 중단: {}", e.getMessage());
                break;
            } catch (Exception e) {
                // 특정 월 실패해도 다음 달 크롤링 계속 진행
                log.error("[{}년 {}월] 크롤링 실패: {}",
                    targetMonth.getYear(), targetMonth.getMonthValue(), e.getMessage());
            }
        }
    }

    /**
     * 특정 연월 크롤링 및 DB 저장
     */
    @Transactional
    public void crawlMonth(int year, int month) throws IOException {
        String url = buildUrl(year, month);
        log.info("[크롤링 시작] {}년 {}월 - URL: {}", year, month, url);

        Document doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
            .timeout(CONNECTION_TIMEOUT_MS)
            .get();

        List<TaxSchedule> schedules = parseSchedules(doc, year, month, url);
        upsertSchedules(schedules);

        log.info("[크롤링 완료] {}년 {}월 - {}건 처리", year, month, schedules.size());
    }

    /**
     * HTML 파싱 - tbody > tr 순회하며 데이터 추출
     *
     * HTML 구조:
     * <tr>
     *   <td>03</td>            ← 월
     *   <td>03</td>            ← 일
     *   <td class="bbs_tit">항목명</td>
     * </tr>
     */
    private List<TaxSchedule> parseSchedules(Document doc, int year, int month, String url) {
        List<TaxSchedule> result = new ArrayList<>();

        // table.mgt5 안의 tbody > tr 전체 순회
        Elements rows = doc.select("table.mgt5 tbody tr");

        if (rows.isEmpty()) {
            log.warn("[파싱 경고] {}년 {}월 - 데이터 없음 (HTML 구조 변경 가능성)", year, month);
            return result;
        }

        for (Element row : rows) {
            try {
                TaxSchedule schedule = parseRow(row, year, url);
                if (schedule != null) {
                    result.add(schedule);
                }
            } catch (Exception e) {
                // 특정 행 파싱 실패해도 계속 진행
                log.warn("[행 파싱 실패] 내용: {} / 오류: {}", row.text(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 단일 tr 행 파싱
     */
    private TaxSchedule parseRow(Element row, int year, String url) {
        Elements tds = row.select("td");

        // td가 3개 미만이면 유효하지 않은 행
        if (tds.size() < 3) return null;

        String monthStr = tds.get(0).text().trim();
        String dayStr   = tds.get(1).text().trim();

        // 숫자 유효성 체크
        if (!isNumeric(monthStr) || !isNumeric(dayStr)) return null;

        int month = Integer.parseInt(monthStr);
        int day   = Integer.parseInt(dayStr);

        // 날짜 유효성 체크 (ex: 13월, 32일 방지)
        if (!isValidDate(year, month, day)) {
            log.warn("[날짜 유효성 오류] {}년 {}월 {}일", year, month, day);
            return null;
        }

        // bbs_tit 클래스 td 추출
        Elements titElements = row.select("td.bbs_tit");
        if (titElements.isEmpty()) return null;

        String title = titElements.get(0).text().trim();
        if (title.isEmpty()) return null;

        return TaxSchedule.builder()
            .year(year)
            .month(month)
            .day(day)
            .title(title)
            .sourceUrl(url)
            .build();
    }

    /**
     * Upsert 처리
     * - 이미 존재하면 내용 업데이트
     * - 없으면 새로 삽입
     */
    private void upsertSchedules(List<TaxSchedule> schedules) {
        int inserted = 0, updated = 0;

        for (TaxSchedule schedule : schedules) {
            var existing = taxScheduleRepository
                .findByScheduleDateAndTitle(schedule.getScheduleDate(), schedule.getTitle());

            if (existing.isPresent()) {
                existing.get().update(schedule.getTitle());
                updated++;
            } else {
                taxScheduleRepository.save(schedule);
                inserted++;
            }
        }

        log.info("[DB 저장] 신규: {}건, 업데이트: {}건", inserted, updated);
    }

    /**
     * URL 생성
     * ex) taxYear=2026&taxMonth=03
     */
    private String buildUrl(int year, int month) {
        return String.format("%s?taxYear=%d&taxMonth=%02d&mi=%s",
            BASE_URL, year, month, MI_PARAM);
    }

    private boolean isNumeric(String str) {
        return str != null && str.matches("\\d+");
    }

    private boolean isValidDate(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

package hongik.finEdu.calendar.service;

import hongik.finEdu.calendar.entity.IssueSchedule;
import hongik.finEdu.calendar.repository.IssueScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 삼성증권 일간이슈 ({@code dayDataList.do}) — 월 단위로 수집해 DB에 적재.
 */
@Slf4j
@Service
public class IssueScheduleService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final IssueScheduleRepository issueScheduleRepository;
    private final ObjectMapper objectMapper;

    private final RestClient restClient;
    private final String listPath;
    private final String sourceLabel;
    private final int maxRowsPerDay;
    private final long betweenDayDelayMs;

    public IssueScheduleService(
            IssueScheduleRepository issueScheduleRepository,
            ObjectMapper objectMapper,
            @Value("${issue-calendar.samsung-origin:https://www.samsungpop.com}") String samsungOrigin,
            @Value("${issue-calendar.list-path:/ux/kor/invest/issue/dayDataList.do}") String listPath,
            @Value("${issue-calendar.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${issue-calendar.read-timeout-ms:30000}") int readTimeoutMs,
            @Value("${issue-calendar.max-rows-per-day:500}") int maxRowsPerDay,
            @Value("${issue-calendar.between-day-delay-ms:400}") long betweenDayDelayMs) {
        this.issueScheduleRepository = issueScheduleRepository;
        this.objectMapper = objectMapper;
        this.listPath = listPath;
        this.sourceLabel = samsungOrigin + listPath;
        this.maxRowsPerDay = maxRowsPerDay;
        this.betweenDayDelayMs = betweenDayDelayMs;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(samsungOrigin)
                .requestFactory(factory)
                .build();
    }

    /**
     * 해당 연·월 DB에서 <strong>일자마다</strong> 분류(category)별 첫 한 건만
     * (같은 날·같은 category 는 day·id 순 첫 행).
     */
    public List<IssueSchedule> firstPerCategoryPerDayForMonth(int year, int month) {
        List<IssueSchedule> rows = issueScheduleRepository.findByYearAndMonthOrderByDayAscIdAsc(year, month);
        Map<String, IssueSchedule> firstByDayAndCategory = new LinkedHashMap<>();
        for (IssueSchedule row : rows) {
            String key = row.getScheduleDate() + "|" + row.getCategory();
            firstByDayAndCategory.putIfAbsent(key, row);
        }
        List<IssueSchedule> out = new ArrayList<>(firstByDayAndCategory.values());
        out.sort(Comparator.comparing(IssueSchedule::getScheduleDate).thenComparing(IssueSchedule::getCategory));
        return out;
    }

    /**
     * 해당 연·월 일자별 API 결과를 합쳐, DB에 없는 {@code schedule_date + seq_no} 만 삽입.
     * 이미 있으면 스킵(내용·삭제 모두 하지 않음, 소스 변경 가정 없음).
     */
    @Transactional
    public int importMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<ParsedRow> incoming = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            incoming.addAll(fetchRowsForDate(d));
            if (betweenDayDelayMs > 0 && d.isBefore(end)) {
                sleepQuiet(betweenDayDelayMs);
            }
        }

        Set<String> knownKeys = new HashSet<>();
        for (IssueSchedule e : issueScheduleRepository.findByYearAndMonthOrderByDayAscIdAsc(year, month)) {
            knownKeys.add(naturalKey(e.getScheduleDate(), e.getSeqNo()));
        }

        List<IssueSchedule> newRows = new ArrayList<>();
        for (ParsedRow r : incoming) {
            LocalDate day = r.date();
            String seq = normalizeSeq(r.seqNo());
            if (seq.isEmpty()) {
                seq = "noseq-" + Math.floorMod(Objects.hash(day, r.title(), r.groupCode()), Integer.MAX_VALUE);
            }
            String key = naturalKey(day, seq);
            if (!knownKeys.add(key)) {
                continue;
            }
            newRows.add(IssueSchedule.builder()
                    .year(day.getYear())
                    .month(day.getMonthValue())
                    .day(day.getDayOfMonth())
                    .category(r.category())
                    .title(r.title())
                    .groupCode(r.groupCode())
                    .seqNo(seq)
                    .sourceUrl(sourceLabel)
                    .build());
        }

        if (!newRows.isEmpty()) {
            issueScheduleRepository.saveAll(newRows);
        }

        log.info("[issue-schedule] {}-{:02d} 수집 {}건 중 신규 삽입 {}건(기존 키는 스킵)",
                year, month, incoming.size(), newRows.size());
        return newRows.size();
    }

    private static String naturalKey(LocalDate scheduleDate, String seqNo) {
        return scheduleDate + "|" + normalizeSeq(seqNo);
    }

    private static String normalizeSeq(String seq) {
        return seq == null ? "" : seq.trim();
    }

    private List<ParsedRow> fetchRowsForDate(LocalDate date) {
        String ymd = date.format(YYYYMMDD);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("searchStart", ymd);
        form.add("searchEnd", ymd);
        form.add("A_Period1", "3");
        form.add("P_PrntNumber", String.valueOf(maxRowsPerDay));
        form.add("P_ReRefrYN", "N");
        form.add("P_Filler3", "000");
        form.add("P_KeyWord", "");
        form.add("A_ProcSectCd", "1");

        String body = restClient.post()
                .uri(listPath)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Requested-With", "XMLHttpRequest")
                .body(form)
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank() || !body.contains("\"outRec2\"")) {
            log.debug("[issue-schedule] 일자 {} 응답 없음 또는 목록 필드 없음", date);
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode outRec2 = root.path("result").path("outRec2");
            if (!outRec2.isArray()) {
                return List.of();
            }

            List<ParsedRow> list = new ArrayList<>();
            for (JsonNode node : outRec2) {
                String pDt1 = text(node, "p_Dt1");
                String grp = text(node, "p_GrpCd1");
                String title = text(node, "ntcTitle");
                String seq = text(node, "seqNo1");
                if (seq.isEmpty() && title.isEmpty() && grp.isEmpty()) {
                    continue;
                }
                LocalDate rowDate = parseYyyyMmDd(pDt1, date);
                String label = categoryLabel(grp);
                list.add(new ParsedRow(rowDate, label, title, grp, normalizeSeq(seq)));
            }
            return list;
        } catch (JacksonException e) {
            log.error("[issue-schedule] JSON 파싱 실패 ({}): {}", date, e.getMessage());
            return List.of();
        }
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("일간이슈 수집 중단됨", e);
        }
    }

    private static LocalDate parseYyyyMmDd(String raw, LocalDate fallback) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() >= 8) {
            try {
                return LocalDate.parse(digits.substring(0, 8), YYYYMMDD);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return "";
        }
        String s = node.path(field).asString();
        return s != null ? s.trim() : "";
    }

    static String categoryLabel(String grpCdRaw) {
        if (grpCdRaw == null) {
            return "기타";
        }
        String code = grpCdRaw.trim();
        return switch (code) {
            case "001" -> "국내종목(실적)";
            case "002" -> "국내지표";
            case "003" -> "국내이슈";
            case "004" -> "해외종목(실적 등)";
            case "005" -> "해외지표";
            case "006" -> "해외이슈";
            case "007" -> "신규상장/공모청약";
            case "008" -> "기타(영화,음악등)";
            case "009" -> "국내외휴장";
            case "010" -> "스포츠";
            case "011" -> "변경상장/재상장";
            case "012", "013", "014", "015" -> "국내종목(기타)";
            default -> "기타(" + code + ")";
        };
    }

    private record ParsedRow(
            LocalDate date,
            String category,
            String title,
            String groupCode,
            String seqNo
    ) {}
}

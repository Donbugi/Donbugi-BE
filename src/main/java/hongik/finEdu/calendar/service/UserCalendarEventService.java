package hongik.finEdu.calendar.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.calendar.dto.Meridiem;
import hongik.finEdu.calendar.dto.UserCalendarEventCreateRequest;
import hongik.finEdu.calendar.dto.UserCalendarEventResponseDto;
import hongik.finEdu.calendar.dto.UserCalendarEventUpdateRequest;
import hongik.finEdu.calendar.entity.UserCalendarEvent;
import hongik.finEdu.calendar.repository.UserCalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCalendarEventService {

    private static final int TITLE_MAX = 200;
    private static final int MEMO_MAX = 5000;

    private final UserCalendarEventRepository repository;

    @Transactional
    public UserCalendarEventResponseDto create(String userId, UserCalendarEventCreateRequest req) {
        String title = validateTitle(req.title());
        LocalDate date = parseDate(req.year(), req.month(), req.day());
        Meridiem meridiem = parseMeridiem(req.meridiem());
        validateClock(req.hour(), req.minute());
        LocalTime time = toLocalTime(meridiem, req.hour(), req.minute());
        String memo = normalizeMemo(req.memo());

        UserCalendarEvent saved = repository.save(UserCalendarEvent.builder()
                .userId(requireUserId(userId))
                .title(title)
                .eventDate(date)
                .eventTime(time)
                .memo(memo)
                .build());

        return toDto(saved);
    }

    @Transactional
    public UserCalendarEventResponseDto update(String userId, Long eventId, UserCalendarEventUpdateRequest req) {
        UserCalendarEvent event = findOwned(userId, eventId);
        String title = validateTitle(req.title());
        LocalDate date = parseDate(req.year(), req.month(), req.day());
        Meridiem meridiem = parseMeridiem(req.meridiem());
        validateClock(req.hour(), req.minute());
        LocalTime time = toLocalTime(meridiem, req.hour(), req.minute());
        String memo = normalizeMemo(req.memo());
        event.update(title, date, time, memo);
        return toDto(event);
    }

    @Transactional
    public void delete(String userId, Long eventId) {
        UserCalendarEvent event = findOwned(userId, eventId);
        repository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<UserCalendarEventResponseDto> listByYearMonth(String userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return listByRange(userId, from, to);
    }

    @Transactional(readOnly = true)
    public List<UserCalendarEventResponseDto> listByRange(String userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 앞설 수 없습니다.");
        }
        return repository.findByUserIdAndEventDateBetweenOrderByEventDateAscEventTimeAsc(
                        requireUserId(userId), from, to).stream()
                .map(this::toDto)
                .toList();
    }

    private UserCalendarEvent findOwned(String userId, Long eventId) {
        return repository.findByIdAndUserId(eventId, requireUserId(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "일정 제목은 필수입니다.");
        }
        String t = title.trim();
        if (t.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "일정 제목은 " + TITLE_MAX + "자 이내여야 합니다.");
        }
        return t;
    }

    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String m = memo.trim();
        if (m.length() > MEMO_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "메모는 " + MEMO_MAX + "자 이내여야 합니다.");
        }
        return m;
    }

    private Meridiem parseMeridiem(String raw) {
        try {
            return Meridiem.fromClientString(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
    }

    private LocalDate parseDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "유효한 날짜(년·월·일)를 입력해 주세요.");
        }
    }

    private void validateClock(int hour, int minute) {
        if (hour < 1 || hour > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시(hour)는 1~12 사이여야 합니다.");
        }
        if (minute < 0 || minute > 59) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "분(minute)은 0~59 사이여야 합니다.");
        }
    }

    /** 오전/오후 + 12시간제 시 → {@link LocalTime} */
    public static LocalTime toLocalTime(Meridiem meridiem, int hour12, int minute) {
        int hour24 = switch (meridiem) {
            case AM -> (hour12 == 12) ? 0 : hour12;
            case PM -> (hour12 == 12) ? 12 : hour12 + 12;
        };
        return LocalTime.of(hour24, minute);
    }

    private UserCalendarEventResponseDto toDto(UserCalendarEvent e) {
        LocalTime t = e.getEventTime();
        int h = t.getHour();
        int m = t.getMinute();
        Meridiem mer;
        int hour12;
        if (h == 0) {
            mer = Meridiem.AM;
            hour12 = 12;
        } else if (h < 12) {
            mer = Meridiem.AM;
            hour12 = h;
        } else if (h == 12) {
            mer = Meridiem.PM;
            hour12 = 12;
        } else {
            mer = Meridiem.PM;
            hour12 = h - 12;
        }
        LocalDate d = e.getEventDate();
        return new UserCalendarEventResponseDto(
                e.getId(),
                e.getTitle(),
                d.getYear(),
                d.getMonthValue(),
                d.getDayOfMonth(),
                mer,
                hour12,
                m,
                e.getMemo()
        );
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }
}

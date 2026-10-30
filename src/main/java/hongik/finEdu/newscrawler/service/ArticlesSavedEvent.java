package hongik.finEdu.newscrawler.service;

import java.util.List;

/** 크롤링으로 새 기사가 저장됨 (본문 있는 기사 id 목록) */
public record ArticlesSavedEvent(List<Long> articleIds) {
}

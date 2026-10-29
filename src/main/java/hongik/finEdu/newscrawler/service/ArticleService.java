package hongik.finEdu.newscrawler.service;
import hongik.finEdu.newscrawler.repository.ArticleRepository;
import hongik.finEdu.newscrawler.entity.Article;
import hongik.finEdu.newscrawler.entity.ArticleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public int saveAll(List<ArticleDto> dtos) {
        if (dtos.isEmpty()) return 0;

        // URL 목록 한 번에 중복 조회 (N번 → 1번 쿼리)
        List<String> urls = dtos.stream().map(ArticleDto::getUrl).toList();
        Set<String> existingUrls = articleRepository.findExistingUrls(urls);

        List<Article> toSave = dtos.stream()
                .filter(dto -> !existingUrls.contains(dto.getUrl()))
                .map(dto -> Article.builder()
                        .url(dto.getUrl())
                        .title(dto.getTitle())
                        .content(dto.getContent())
                        .press(dto.getPress())
                        .journalist(dto.getJournalist())
                        .publishedAt(dto.getPublishedAt())
                        .category(dto.getCategory())
                        .collectedAt(LocalDateTime.now())
                        .isSummarized(false)
                        .build())
                .collect(Collectors.toList());

        List<Article> saved = articleRepository.saveAll(toSave);
        log.info("저장: {}개 / 중복 스킵: {}개", toSave.size(), dtos.size() - toSave.size());

        // 커밋 후 퀴즈 사전 생성 트리거 (사용자 요청 경로에서 AI 호출 제거)
        List<Long> ids = saved.stream()
                .filter(a -> a.getContent() != null && !a.getContent().isBlank())
                .map(Article::getArticleId)
                .toList();
        if (!ids.isEmpty()) {
            eventPublisher.publishEvent(new ArticlesSavedEvent(ids));
        }

        return toSave.size();
    }
}
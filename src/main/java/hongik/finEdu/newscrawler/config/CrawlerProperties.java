package hongik.finEdu.newscrawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    private long delayMin = 1500;
    private long delayMax = 3000;
    private List<CategoryEntry> categories = List.of();

    @Getter
    @Setter
    public static class CategoryEntry {
        private String name;
        private String url;
    }

    /** name -> url Map 변환 */
    public Map<String, String> getCategoriesAsMap() {
        return categories.stream()
                .collect(Collectors.toMap(CategoryEntry::getName, CategoryEntry::getUrl, (a, b) -> a, LinkedHashMap::new));
    }
}

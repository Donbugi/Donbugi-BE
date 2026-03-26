package hongik.finEdu.newscrawler.entity;


import lombok.*;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ArticleDto {
    private String url;
    private String title;
    private String content;
    private String press;
    private String journalist;
    private String publishedAt;
    private String category;
}

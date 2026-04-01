package hongik.finEdu.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RandomQuizPackItemDto {

    private Long articleId;
    private String title;
    private String category;
    private List<QuizResponseDto> quizzes;
}

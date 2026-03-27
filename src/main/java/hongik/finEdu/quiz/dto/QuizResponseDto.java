package hongik.finEdu.quiz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponseDto {

    private String question;

    private List<String> options;

    @JsonProperty("correct_index")
    private int correctIndex;

    private String explanation;
}

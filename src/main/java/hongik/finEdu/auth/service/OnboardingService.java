package hongik.finEdu.auth.service;

import hongik.finEdu.auth.dto.OnboardingSubmitRequest;
import hongik.finEdu.auth.dto.OnboardingSubmitResponse;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.service.PointLedgerService;
import hongik.finEdu.user.domain.CharacterLevel;
import hongik.finEdu.user.domain.FinIqLevelPolicy;
import hongik.finEdu.user.entity.AppUser;
import hongik.finEdu.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final int EXPECTED_QUESTIONS = 4;
    private static final List<Integer> VALID_SCORES = List.of(1, 2, 3, 5);

    private final AppUserRepository appUserRepository;

    @Transactional
    public OnboardingSubmitResponse submit(String externalUserId, OnboardingSubmitRequest request) {
        AppUser user = appUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "사용자를 찾을 수 없습니다."));
        if (user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        List<Integer> scores = request.scores();
        if (scores == null || scores.size() != EXPECTED_QUESTIONS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "온보딩 점수는 " + EXPECTED_QUESTIONS + "개여야 합니다.");
        }
        for (Integer score : scores) {
            if (score == null || !VALID_SCORES.contains(score)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "각 문항 점수는 1, 2, 3, 5 중 하나여야 합니다.");
            }
        }
        int total = scores.stream().mapToInt(Integer::intValue).sum();
        CharacterLevel character = CharacterLevel.fromTotalScore(total);
        user.completeOnboarding(character.getLevel(), scores, total);
        appUserRepository.save(user);
        return toResponse(character, total);
    }

    private static OnboardingSubmitResponse toResponse(CharacterLevel character, int total) {
        return new OnboardingSubmitResponse(
                character.getLevel(),
                character.getEmoji(),
                character.getName(),
                character.getTag(),
                total
        );
    }

    @Transactional(readOnly = true)
    public OnboardingSubmitResponse getResult(String externalUserId) {
        AppUser user = appUserRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "사용자를 찾을 수 없습니다."));
        if (!user.isOnboardingCompleted() || user.getCharacterLevel() == null) {
            throw new BusinessException(ErrorCode.ONBOARDING_NOT_COMPLETED);
        }
        CharacterLevel character = CharacterLevel.fromLevel(user.getCharacterLevel());
        return toResponse(character, user.getOnboardingTotalScore() != null ? user.getOnboardingTotalScore() : 0);
    }
}

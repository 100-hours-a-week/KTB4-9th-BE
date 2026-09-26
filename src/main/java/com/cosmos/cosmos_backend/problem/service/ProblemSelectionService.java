package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.common.Category;
import com.cosmos.cosmos_backend.common.Difficulty;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.entity.DailyGeneratedCount;
import com.cosmos.cosmos_backend.problem.domain.entity.Problem;
import com.cosmos.cosmos_backend.problem.dto.response.ProblemSelectionResponse;
import com.cosmos.cosmos_backend.problem.repository.DailyGeneratedCountRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemExampleRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class ProblemSelectionService {

    private static final int DAILY_LIMIT = 3;

    private final ProblemRepository problemRepository;
    private final ProblemService problemService;
    private final ProblemExampleRepository problemExampleRepository;
    private final DailyGeneratedCountRepository dailyGeneratedCountRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 문제 생성(선택). 안 푼 문제를 골라주고 오늘 사용 횟수를 올린다. */
    public ProblemSelectionResponse select(Long userId, String levelParam, String categoryParam) {
        // 1. 입력 검증 (level 필수, category는 없거나 RANDOM이면 null = 카테고리 무관)
        Difficulty difficulty = parseLevel(levelParam);
        Category category = parseCategory(categoryParam);
        LocalDate today = LocalDate.now(clock);

        // 2. 오늘 이미 한도를 다 썼으면 여기서 바로 429 (잠금 없는 빠른 차단)
        int usedBefore = dailyGeneratedCountRepository.findCount(userId, today).orElse(0);
        if (usedBefore >= DAILY_LIMIT) {
            throw limitExceeded(today, usedBefore);
        }

        // 3. 안 푼 문제를 랜덤으로 조회, 없으면 AI에게 문제 생성을 요청해 저장한 문제를 사용
        Problem problem = problemRepository
                .findRandomUnsolved(difficulty.name(), category == null ? null : category.name(), userId)
                .orElse(null);
        if (problem == null) {
            problem = generateProblem(difficulty, category);
        }

        // 4. 문제를 확보했으니 이제 횟수를 +1 (여기만 트랜잭션)
        int usedCount = transactionTemplate.execute(status -> increaseCount(userId, today));

        // 5. 예시를 조회해서 응답 조립
        return ProblemSelectionResponse.of(
                problem,
                problemExampleRepository.findByProblemIdOrderByDisplayOrder(problem.getId()),
                today,
                usedCount,
                DAILY_LIMIT
        );
    }

    // 안 푼 문제가 없을 때 AI에게 문제 생성을 요청해 저장한 문제를 받는다
    private Problem generateProblem(Difficulty difficulty, Category category) {
        // 1. 카테고리가 랜덤이면 16개 중 하나를 무작위로 골라 요청 (AI 요청의 category는 필수라서)
        Category target = category != null
                ? category
                : Category.values()[ThreadLocalRandom.current().nextInt(Category.values().length)];
        try {
            // 2. 문제 생성 요청 + 저장 (ProblemService의 온디맨드 로직 사용)
            return problemService.createOnDemandProblem(difficulty, target);
        } catch (RestClientException e) {
            // 3. AI 서버에 연결하지 못했거나 오류 응답이면 503 (횟수는 올라가지 않음)
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "problem_generation_unavailable");
        }
    }

    // 오늘 횟수를 잠근 채로 확인하고 +1 (트랜잭션 안에서 호출됨)
    private int increaseCount(Long userId, LocalDate today) {
        return dailyGeneratedCountRepository.findForUpdateByUserIdAndUsageDate(userId, today)
                .map(count -> {
                    // 1. 잠근 뒤 다시 확인 (앞의 빠른 확인 이후 다른 요청이 올렸을 수 있음)
                    if (count.getGenerationCount() >= DAILY_LIMIT) {
                        throw limitExceeded(today, count.getGenerationCount());
                    }
                    // 2. +1 (커밋 때 UPDATE가 나감)
                    count.increase();
                    return count.getGenerationCount();
                })
                // 3. 오늘 처음이면 횟수 1로 새로 저장
                .orElseGet(() -> dailyGeneratedCountRepository.save(new DailyGeneratedCount(userId, today)).getGenerationCount());
    }

    // 429 예외 생성 (data에 남은 횟수와 초기화 시각을 담음)
    private BusinessException limitExceeded(LocalDate today, int usedCount) {
        // 1. 초기화 시각은 다음 날 0시(KST)
        OffsetDateTime resetAt = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();
        // 2. 429와 함께 남은 횟수 정보를 담아 반환
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "daily_problem_limit_exceeded",
                new ProblemSelectionResponse.DailyLimitExceededData(today, DAILY_LIMIT, usedCount, DAILY_LIMIT - usedCount, resetAt));
    }

    // "3" 또는 "LV3"를 Difficulty로 변환
    private Difficulty parseLevel(String level) {
        // 1. level이 없으면 400
        if (level == null || level.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "problem_level_is_required");
        }
        try {
            // 2. 숫자만 온 경우 LV를 붙여서 enum으로 변환
            return Difficulty.valueOf(level.startsWith("LV") ? level : "LV" + level);
        } catch (IllegalArgumentException e) {
            // 3. 없는 값이면 400
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_problem_level");
        }
    }

    // category 문자열을 Category로 변환 (없거나 RANDOM이면 null)
    private Category parseCategory(String category) {
        // 1. 없거나 RANDOM이면 카테고리 무관
        if (category == null || category.isBlank() || category.equals("RANDOM")) {
            return null;
        }
        try {
            // 2. 문자열을 enum으로 변환
            return Category.valueOf(category);
        } catch (IllegalArgumentException e) {
            // 3. 없는 값이면 400
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_problem_category");
        }
    }
}

package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.common.Language;
import com.cosmos.cosmos_backend.common.exception.BusinessException;
import com.cosmos.cosmos_backend.problem.domain.HintType;
import com.cosmos.cosmos_backend.problem.domain.entity.Hint;
import com.cosmos.cosmos_backend.problem.domain.entity.UsedHint;
import com.cosmos.cosmos_backend.problem.dto.response.HintResponse;
import com.cosmos.cosmos_backend.problem.repository.HintRepository;
import com.cosmos.cosmos_backend.problem.repository.ProblemRepository;
import com.cosmos.cosmos_backend.problem.repository.UsedHintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HintService {

    private final ProblemRepository problemRepository;
    private final HintRepository hintRepository;
    private final UsedHintRepository usedHintRepository;

    /** 힌트 조회. 조회에 성공했을 때만 사용 단계를 기록. */
    @Transactional
    public HintResponse getHint(Long userId, Long problemId, String language, HintType hintType) {
        // 1. 문제가 있는지 확인 (없으면 404 예외를 던짐)
        if (!problemRepository.existsById(problemId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "problem_not_found");
        }

        // 2. language 문자열을 enum으로 변환 (없는 값이면 400 예외를 던짐)
        Language lang = parseLanguage(language);

        // 3. 문제·언어·유형에 맞는 힌트를 조회 (없으면 404 예외를 던짐)
        Hint hint = hintRepository.findByProblemIdAndLanguageAndHintType(problemId, lang, hintType)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, notFoundMessage(hintType)));

        // 4. 사용 단계 기록 (주석=1, 정답=2). 기록이 있으면 올리기만 하고, 없으면 새로 저장
        int stage = hintType == HintType.COMMENT ? 1 : 2;
        usedHintRepository.findByUserIdAndProblemId(userId, problemId)
                .ifPresentOrElse(
                        used -> used.raiseStageTo(stage),
                        () -> usedHintRepository.save(new UsedHint(userId, problemId, stage))
                );

        // 5. 응답 형태로 조립해서 반환
        return HintResponse.of(problemId, hintType, stage, hint.getContent());
    }

    // language 문자열을 Language enum으로 변환
    private Language parseLanguage(String value) {
        try {
            // 1. 문자열을 enum으로 변환
            return Language.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 2. 없는 값이면 400 예외를 던짐
            throw new BusinessException(HttpStatus.BAD_REQUEST, "invalid_language");
        }
    }

    // 힌트 유형별 404 message
    private String notFoundMessage(HintType hintType) {
        return hintType == HintType.COMMENT ? "comment_hint_not_found" : "answer_hint_not_found";
    }
}

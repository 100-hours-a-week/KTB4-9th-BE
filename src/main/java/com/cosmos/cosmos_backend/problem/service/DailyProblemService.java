package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.problem.domain.entity.*;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.domain.*;
import com.cosmos.cosmos_backend.problem.dto.response.DailyProblemResponseDto;
import com.cosmos.cosmos_backend.problem.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyProblemService {

    private final ProblemService problemService;

    private final ProblemExampleRepository problemExampleRepository;

    private final DailyProblemRepository dailyProblemRepository;

    private final Clock clock;

    @Transactional
    public void createDailyProblems(AiProblemsCreateRequestDto problemsCreateRequest) {

        // 1. 문제 저장
        List<Problem> problems = problemService.createProblems(problemsCreateRequest);

        // 2. 내일 날짜
        LocalDate dailyProblemDate = LocalDate.now(clock).plusDays(1);

        // 3. 저장된 문제들을 DailyProblem으로 등록
        int displayOrder = 1;

        for (Problem problem : problems) {

            DailyProblem dailyProblem = new DailyProblem(
                    problem,
                    dailyProblemDate,
                    displayOrder
            );

            dailyProblemRepository.save(dailyProblem);

            displayOrder++;
        }
    }

    // 데일리 문제 조회 -> FE에서 처음 화면에서 조회
    // 로컬 캐시 먼저 조회
    @Cacheable(
            cacheManager = "caffeineCacheManager",
            cacheNames = "dailyProblems",
            key = "T(java.time.LocalDate).now(@clock)"
    )
    public DailyProblemResponseDto getDailyProblems() {

        // 오늘 날짜
        LocalDate date = LocalDate.now(clock);

        // 1. 데일리 문제 리스트 디비에서 받아오기
        List<DailyProblem> dailyProblemList = dailyProblemRepository.findWithProblemByRecommendDateOrderByDisplayOrderDesc(date);

        //2. 데일리 문제 id 리스트로 받아오기
        List<Long> problemIdList = dailyProblemList.stream()
                .map(dailyProblem -> dailyProblem.getProblem().getId())
                .toList();

        // 3. 문제 id로 문제 예시 리스트 받아오기
        List<ProblemExample> problemExamples = problemExampleRepository.findByProblemIdInOrderByProblemIdAscDisplayOrderAsc(problemIdList);

        // 4. 문제 id, 문제 예시 리스트로 map 만들기
        Map<Long, List<ProblemExample>> examplesByProblemId = problemExamples.stream().collect(Collectors.groupingBy(ProblemExample::getProblemId));

        // 5. 응답 DTO 구성
        List<DailyProblemResponseDto.DailyProblems> dailyProblems =
                dailyProblemList.stream()
                        .map(dailyProblem -> {

                            Problem problem = dailyProblem.getProblem();

                            List<AiProblemsCreateRequestDto.ProblemExamples> exampleDtos =
                                    examplesByProblemId
                                            .getOrDefault(
                                                    problem.getId(),
                                                    List.of()
                                            )
                                            .stream()
                                            .map(example ->
                                                    new AiProblemsCreateRequestDto.ProblemExamples(
                                                            example.getInput(),
                                                            example.getOutput(),
                                                            example.getDescription()
                                                    )
                                            )
                                            .toList();

                            return new DailyProblemResponseDto.DailyProblems(
                                    problem.getId(),
                                    problem.getDifficulty(),
                                    problem.getCategory(),
                                    problem.getTitle(),
                                    problem.getContent(),
                                    exampleDtos
                            );
                        })
                        .toList();

        return new DailyProblemResponseDto(
                date,
                dailyProblems
        );
    }
}

package com.cosmos.cosmos_backend.problem.service;

import com.cosmos.cosmos_backend.problem.domain.entity.*;
import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.domain.*;
import com.cosmos.cosmos_backend.problem.dto.response.DailyProblemResponseDto;
import com.cosmos.cosmos_backend.problem.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DailyProblemService {

    private final ProblemService problemService;

    private final ProblemExampleRepository problemExampleRepository;

    private final DailyProblemRepository dailyProblemRepository;

    @Transactional
    public void createDailyProblems(AiProblemsCreateRequestDto problemsCreateRequest) {

        // 1. 문제 저장
        List<Problem> problems = problemService.createProblems(problemsCreateRequest);

        // 2. 오늘 날짜
        LocalDate dailyProblemDate = LocalDate.now();

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
    public DailyProblemResponseDto getDailyProblems() {

        // 오늘 날짜
        LocalDate date = LocalDate.now();

        // 1. 데일리 문제 리스트 디비에서 받아오기
        List<DailyProblem> dailyProblemList = dailyProblemRepository.findByRecommendDateOrderByDisplayOrderDesc(date);

        // 2. 데일리 문제 리스트로 응답 구성
        List<DailyProblemResponseDto.DailyProblems> dailyProblems = new ArrayList<>();

        for (int i = 0; i < dailyProblemList.size(); i++) {

            Problem problem = dailyProblemList.get(i).getProblem();

            List<AiProblemsCreateRequestDto.ProblemExamples> examples = new ArrayList<>();

            List<ProblemExample> problemExamples = problemExampleRepository.findByProblemIdOrderByDisplayOrder(problem.getId());

            for (int j = 0 ; j < problemExamples.size(); j++) {
                AiProblemsCreateRequestDto.ProblemExamples example = new AiProblemsCreateRequestDto.ProblemExamples(
                        problemExamples.get(j).getInput(),
                        problemExamples.get(j).getOutput(),
                        problemExamples.get(j).getDescription()
                );

                examples.add(example);
            }

            DailyProblemResponseDto.DailyProblems dailyProblem = new DailyProblemResponseDto.DailyProblems(
                    problem.getId(),
                    problem.getDifficulty(),
                    problem.getCategory(),
                    problem.getTitle(),
                    problem.getContent(),
                    examples
            );

            dailyProblems.add(dailyProblem);
        }

        DailyProblemResponseDto dailyProblemResponse = new DailyProblemResponseDto(
                date,
                dailyProblems
        );

        return dailyProblemResponse;
    }
}

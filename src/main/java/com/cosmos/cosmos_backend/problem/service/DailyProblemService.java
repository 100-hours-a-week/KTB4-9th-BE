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

    private final ProblemRepository problemRepository;

    private final ProblemExampleRepository problemExampleRepository;

    private final RunningLimitRepository runningLimitRepository;

    private final TestCaseRepository testCaseRepository;

    private final KeywordRepository keywordRepository;

    private final HintRepository hintRepository;

    private final DailyProblemRepository dailyProblemRepository;

    @Transactional
    public void createDailyProblems(AiProblemsCreateRequestDto problemsCreateRequest) {

        List<AiProblemsCreateRequestDto.ProblemsInfo> aiProblems = problemsCreateRequest.aiProblems();

        // 오늘 날짜
        LocalDate dailyProblemDate = LocalDate.now();

        // 필요한 엔티티 선언
        DailyProblem dailyProblem;

        Problem problem;

        ProblemExample problemExample;

        RunningLimit runningLimit;

        TestCase testCase;

        Keyword keyword;

        Hint hintComment;

        Hint hintSolution;

        // 개수
        Integer displayOrder = 1;

        for (AiProblemsCreateRequestDto.ProblemsInfo problemsInfo : aiProblems) {

            problem = new Problem(
                    problemsInfo.difficulty(),
                    problemsInfo.category(),
                    problemsInfo.problemTitle(),
                    problemsInfo.problemDescription(),
                    problemsInfo.inputFormat(),
                    problemsInfo.outputFormat(),
                    problemsInfo.inputConstraints(),
                    problemsInfo.categorySelectReason()
            );

            problemRepository.save(problem);

            //데일리 문제 엔티티에도 저장
            dailyProblem = new DailyProblem(problem, dailyProblemDate, displayOrder);

            displayOrder ++;

            dailyProblemRepository.save(dailyProblem);

            Long dailyProblemId = problem.getId();

            // 입출력 예시
            for (int j = 0; j < problemsInfo.problemExamples().size(); j++) {
                problemExample = new ProblemExample(
                        dailyProblemId,
                        problemsInfo.problemExamples().get(j).input(),
                        problemsInfo.problemExamples().get(j).output(),
                        problemsInfo.problemExamples().get(j).description(),
                        j + 1
                );

                problemExampleRepository.save(problemExample);

            }

            // 언어별 정보 저장, 실행 조건, 힌트
            for(int k = 0 ; k < problemsInfo.executionLimit().size() ; k ++){
                runningLimit = new RunningLimit(
                        dailyProblemId,
                        problemsInfo.executionLimit().get(k).language(),
                        problemsInfo.executionLimit().get(k).timeLimitMs(),
                        problemsInfo.executionLimit().get(k).memoryLimitKb()
                );

                runningLimitRepository.save(runningLimit);

                hintComment = new Hint(
                        dailyProblemId,
                        problemsInfo.hintComments().get(k).language(),
                        HintType.COMMENT,
                        problemsInfo.hintComments().get(k).content()
                );

                hintRepository.save(hintComment);

                hintSolution = new Hint(
                        dailyProblemId,
                        problemsInfo.hintSolutionCodes().get(k).language(),
                        HintType.SOLUTION,
                        problemsInfo.hintSolutionCodes().get(k).content()
                );

                hintRepository.save(hintSolution);
            }

            // 코드 채점용 테스트 케이스
            for (int l = 0 ; l < problemsInfo.hiddenTests().size() ; l ++){
                testCase = new TestCase(
                        dailyProblemId,
                        problemsInfo.hiddenTests().get(l).input(),
                        problemsInfo.hiddenTests().get(l).output(),
                        l + 1
                );

                testCaseRepository.save(testCase);
            }

            // 자연어 문제 풀이용 키퉈드
            for (int m = 0 ; m < problemsInfo.solutionKeywords().size() ; m++){
                keyword = new Keyword(
                        dailyProblemId,
                        problemsInfo.solutionKeywords().get(m)
                );

                keywordRepository.save(keyword);
            }
        }
    }

    public DailyProblemResponseDto getDailyProblems() {

        LocalDate date = LocalDate.now();

        List<DailyProblem> dailyProblemList = dailyProblemRepository.findByRecommendDateOrderByDisplayOrderDesc(date);

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

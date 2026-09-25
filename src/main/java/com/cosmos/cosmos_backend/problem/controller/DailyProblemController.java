package com.cosmos.cosmos_backend.problem.controller;


import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.DailyProblemResponseDto;
import com.cosmos.cosmos_backend.problem.service.DailyProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/daily-problems")
@RequiredArgsConstructor
public class DailyProblemController {

    private final DailyProblemService dailyProblemService;

    // ai -> BE 데일리 문제 저장
    @PostMapping("")
    public ResponseEntity<Void> createDailyProblems(
            @Valid
            @RequestBody AiProblemsCreateRequestDto problemCreateRequest) {

        dailyProblemService.createDailyProblems(problemCreateRequest);

        return ResponseEntity.noContent().build();
    }

    // 홈 화면에서 데일리 문제 표시용
    @GetMapping("")
    public DailyProblemResponseDto getDailyProblems() {

        DailyProblemResponseDto dailyProblemResponse = dailyProblemService.getDailyProblems();

        return dailyProblemResponse;
    }

}

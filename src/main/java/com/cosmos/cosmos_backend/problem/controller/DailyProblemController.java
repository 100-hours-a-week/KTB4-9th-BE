package com.cosmos.cosmos_backend.problem.controller;


import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.dto.response.DailyProblemResponseDto;
import com.cosmos.cosmos_backend.problem.service.DailyProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/daily-problems")
@RequiredArgsConstructor
@Tag(name = "데일리 문제", description = "홈 화면의 데일리 추천 문제 API")
public class DailyProblemController {

    private final DailyProblemService dailyProblemService;

    // ai -> BE 데일리 문제 저장
    @PostMapping("")
    @Operation(summary = "데일리 문제 저장", description = "AI가 생성한 데일리 추천 문제를 저장합니다.", security = {})
    public ResponseEntity<Void> createDailyProblems(
            @Valid
            @RequestBody AiProblemsCreateRequestDto problemCreateRequest) {

        dailyProblemService.createDailyProblems(problemCreateRequest);

        return ResponseEntity.noContent().build();
    }

    // 홈 화면에서 데일리 문제 표시용
    @GetMapping("")
    @Operation(summary = "데일리 문제 조회", description = "오늘의 난이도별 추천 문제를 조회합니다.", security = {})
    public DailyProblemResponseDto getDailyProblems() {

        DailyProblemResponseDto dailyProblemResponse = dailyProblemService.getDailyProblems();

        return dailyProblemResponse;
    }

}

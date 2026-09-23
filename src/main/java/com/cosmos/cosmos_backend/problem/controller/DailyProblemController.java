package com.cosmos.cosmos_backend.problem.controller;


import com.cosmos.cosmos_backend.problem.dto.request.AiProblemsCreateRequestDto;
import com.cosmos.cosmos_backend.problem.service.DailyProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
public class DailyProblemController {

    private final DailyProblemService dailyProblemService;

    // ai -> BE 데일리 문제 저장
    @PostMapping("/daily-problems")
    public ResponseEntity<Void> createDailyProblems(
            @Valid
            @RequestBody AiProblemsCreateRequestDto problemCreateRequest) {

        dailyProblemService.createDailyProblems(problemCreateRequest);

        return ResponseEntity.noContent().build();
    }

}

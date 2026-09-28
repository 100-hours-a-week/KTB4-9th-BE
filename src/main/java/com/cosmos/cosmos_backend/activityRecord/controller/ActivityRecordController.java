package com.cosmos.cosmos_backend.activityRecord.controller;

import com.cosmos.cosmos_backend.activityRecord.dto.ActivityRecordResponseDto;
import com.cosmos.cosmos_backend.activityRecord.service.ActivityRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
@Tag(name = "활동 기록", description = "로그인 사용자의 학습 활동 기록 API")
public class ActivityRecordController {

    private final ActivityRecordService activityRecordService;

    // 잔디 기록 조회 -> 응답으로 20주 데이터 받아감
    @GetMapping("/me")
    @Operation(summary = "내 학습 활동 조회", description = "최근 20주 동안 날짜별로 해결한 문제 수를 조회합니다.")
    public ActivityRecordResponseDto getLearningRecord(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ){

        // 사용자 id 확인
        Long userId = Long.parseLong(jwt.getSubject());

        ActivityRecordResponseDto activityRecordResponseDto = activityRecordService.getLearningRecord(userId);

        return activityRecordResponseDto;
    }


}

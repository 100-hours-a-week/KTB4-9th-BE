package com.cosmos.cosmos_backend.ActivityRecord.controller;

import com.cosmos.cosmos_backend.ActivityRecord.dto.ActivityRecordResponseDto;
import com.cosmos.cosmos_backend.ActivityRecord.service.ActivityRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityRecordController {

    private final ActivityRecordService activityRecordService;

    // 잔디 기록 조회 -> 응답으로 20주 데이터 받아감
    @GetMapping("/me")
    public ActivityRecordResponseDto getLearningRecord(
            @AuthenticationPrincipal Jwt jwt
    ){

        // 사용자 id 확인
        Long userId = Long.parseLong(jwt.getSubject());

        ActivityRecordResponseDto activityRecordResponseDto = activityRecordService.getLearningRecord(userId);

        return activityRecordResponseDto;
    }


}

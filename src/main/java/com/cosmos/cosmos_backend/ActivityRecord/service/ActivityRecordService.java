package com.cosmos.cosmos_backend.ActivityRecord.service;


import com.cosmos.cosmos_backend.ActivityRecord.domain.entity.ActivityRecord;
import com.cosmos.cosmos_backend.ActivityRecord.dto.ActivityRecordResponseDto;
import com.cosmos.cosmos_backend.ActivityRecord.repository.ActivityRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityRecordService {

    private final ActivityRecordRepository activityRecordRepository;

    public ActivityRecordResponseDto getLearningRecord(Long userId) {

        // 1. 조회할 기간 계산하기 (20주 = 140일)
        // 오늘을 포함해서 총 140일로
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(139);

        // 2. 140일 동안 사용자의 학습 기록 조회
        List<ActivityRecord> activityRecordList = activityRecordRepository.findByUserIdAndActivityDateBetween(userId, startDate, endDate);

        // 3. 조회한 학습 기록 바탕으로 응답 조립하기
        List<ActivityRecordResponseDto.Activities> activityList = new ArrayList<>();

        for (int i = 0 ; i < activityRecordList.size(); i++) {
            ActivityRecordResponseDto.Activities activities = new ActivityRecordResponseDto.Activities(activityRecordList.get(i).getActivityDate(), activityRecordList.get(i).getCorrectProblemCount());

            activityList.add(activities);
        }

        // 4. 응답 생성
        ActivityRecordResponseDto activityRecordResponseDto = new ActivityRecordResponseDto(startDate, endDate, activityList);

        return  activityRecordResponseDto;
    }
}

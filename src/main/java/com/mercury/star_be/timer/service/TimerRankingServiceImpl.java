package com.mercury.star_be.timer.service;

import com.mercury.star_be.timer.dto.TimerRankingPeriod;
import com.mercury.star_be.timer.dto.TimerRankingResponseDto;
import com.mercury.star_be.timer.repository.TimerRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimerRankingServiceImpl implements TimerRankingService {

    private final TimerRankingRepository timerRankingRepository;

    /**
       기간 별 집중방 랭킹 조회
       @Param TimerRankingPeriod period : 조회할 기간 (DAILY, WEEKLY, MONTHLY) 일, 주, 월간
     */
    @Override
    public List<TimerRankingResponseDto> getGroupPeriodStudyTimeRanking(Long groupId, TimerRankingPeriod period) {
        // 기간 계산
        LocalDate startDate = getStartDate(period);

        return timerRankingRepository.findRankingByGroupIdAndStartDate(groupId, startDate);
    }

    /**
     * 기간에 따른 시작일 계산
     * @param period : 조회할 기간 (DAILY, WEEKLY, MONTHLY) 일, 주, 월간
     *               DAILY : 오늘, WEEKLY : 이번주 월요일, MONTHLY : 이번달 1일
     * @return LocalDate : 시작일
     */
    private LocalDate getStartDate(TimerRankingPeriod period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case DAILY -> today;
            case WEEKLY -> today.minusDays(today.getDayOfWeek().getValue() - 1);
            case MONTHLY -> today.withDayOfMonth(1);
        };
    }
}

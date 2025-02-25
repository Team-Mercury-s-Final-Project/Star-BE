package com.mercury.star_be.timer.dto;

import com.mercury.star_be.timer.entity.Timer;
import com.mercury.star_be.timer.enums.TimerStatus;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class TimerDto{

    private TimerEvent event;
    private Long timerId;
    private Long userId;
    private String nickname;
    private long timeSoFar;
    private long todayTotalTime;
    private int ranking;
    private String status;
    private int MilliSecondTimeOffset;
    // start인 타이머를 현재 시각 기준으로 계산했을 때의 시간
    private LocalDateTime timeOffsetSetAt;

    public TimerDto(Timer timer) {
        this.timerId = timer.getId();
        this.userId = timer.getUserId();
        this.nickname = timer.getUser().getNickname();
        this.timeSoFar = timer.getTimeSoFar();
        this.todayTotalTime = timer.getTotalTime();
        this.status = timer.getStatus().toString();
    }

    public static TimerDto getEntryEventDtoWithNoTimer() {
        return TimerDto.builder()
                .event(TimerEvent.ENTRY)
                .status("rest")
                .todayTotalTime(0)
                .ranking(0)
                .timeSoFar(0)
                .build();
    }

    /**
     * 현재 시간을 기준으로 타이머의 경과 시간을 계산하여 저장
     * @param timer
     */
    public void setCurrentTimeSoFar(Timer timer){
        // 타이머가 시작되지 않았다면 저장된 시간을 그대로 사용
        if (!this.status.equals(TimerStatus.START.toString())) {
            this.timeSoFar = timer.getTimeSoFar();
            return;
        }
        // 타이머 작동 중이라면, 보정 필요.
        // 마지막 이벤트로부터 저장 된 경과 시간 + 현재 시간 - 시작 시간
        this.timeOffsetSetAt = LocalDateTime.now();
        long milliTimeSoFar = timer.getMilliSecondTimeSoFarFromStartTimeTo(timeOffsetSetAt);
        long secondTimeSoFar = milliTimeSoFar/1000;
        long milliSecondOffset = milliTimeSoFar%1000;
        this.timeSoFar = secondTimeSoFar;
        this.MilliSecondTimeOffset = (int)milliSecondOffset;
    }
}

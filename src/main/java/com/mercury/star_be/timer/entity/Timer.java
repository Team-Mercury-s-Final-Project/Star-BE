package com.mercury.star_be.timer.entity;

import com.mercury.star_be.studygroup.entity.GroupMember;
import com.mercury.star_be.timer.dto.TimerDto;
import com.mercury.star_be.timer.dto.TimerEvent;
import com.mercury.star_be.timer.enums.TimerStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;
import lombok.ToString;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Entity
@Getter
@NoArgsConstructor
@ToString
public class Timer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TimerStatus status;

    private LocalDateTime startTime;

    private long timeSoFar;

    private LocalDateTime endTime;

    private long totalTime;

    private LocalDate studyDate;

    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_member_id")
    private GroupMember groupMember;

    @Builder
    public Timer(Long id, TimerStatus status, LocalDateTime startTime, LocalDateTime endTime,
        LocalDate studyDate, GroupMember groupMember) {
        this.id = id;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.studyDate = studyDate;
        this.groupMember = groupMember;
    }

    public void start() {
        this.status = TimerStatus.START;
        this.startTime = LocalDateTime.now();
        this.endTime = null;
//        this.studyDate = LocalDate.now(); Start 시, Date 비교하고 1일 1 Timer로 저장
    }

    /**
     * Status가 start 일시, 현재까지의 공부 시간 계산
     * 현재 start 시, startTime만 기록하고 TimeSoFar을 실시간으로 업데이트 하지 않기 때문.
     * @Param LocalDateTime.now()만 사용할 것.
     * @Return : 밀리초 단위, 기존 공부 시간+시작 시점부터 현재까지의 공부 시간
     */
    public Long getMilliSecondTimeSoFarFromStartTimeTo(LocalDateTime now) {
        if (this.status != TimerStatus.START) {
            return timeSoFar*1000;
        }
        // 마지막 이벤트로부터 저장 된 경과 시간 + 현재 시간 - 시작 시간
        Duration timeOffset = Duration.between(startTime, now);
        return timeSoFar*1000 + timeOffset.toMillis();
    }

    /**
     * 타이머 중지
     * 자정 계산은 service에서 처리
     */
    public void stop() {
        // 타이머가 시작되지 않은 경우 중지 불가 (End or Stop 상태인 경우 중지 불가)
        if (this.status != TimerStatus.START) {
            return;
        }
        this.status = TimerStatus.STOP;
        // 시작 시점부터 현재까지 공부한 시간 계산
        long elapsedSeconds = Duration.between(startTime, LocalDateTime.now()).getSeconds();
        this.timeSoFar += elapsedSeconds;
        startTime = null;
        // End 안하면 totalTime 유실되기 때문에 추가
        totalTime += elapsedSeconds;
    }

    /**
     * 타이머 종료
     * 시작한 날 + 1일에 종료할 경우 : 시작 시간 기준으로 자정까지 공부한 시간만 계산
     * @return : 자정 이후 초과한 시간 반환.
     */
    public void end(){
        // 오늘인지 확인
        boolean isToday = studyDate.equals(LocalDate.now());

        // 타이머가 START 인지, STOP 인지 확인
        // 종료 요청된 시간.
        this.endTime = LocalDateTime.now();

        // 오늘인 경우
        if (isToday) {
            switch (this.status) {
                case START:
                    // 시작 시점부터 현재까지 공부한 시간 계산
                    long elapsedSeconds = Duration.between(startTime, endTime).getSeconds();
                    this.totalTime += elapsedSeconds;
                    break;
                case STOP:
                    // 그냥 종료. - stop 에서 이미 totalTime 누적함.
                    break;
            }
        }
        // 다음날로 넘어간 경우, 자정까지 공부한 시간만 계산
        else  {
            switch (this.status) {
                case START:
                    // 시작 시점부터 자정까지 공부한 시간 계산
                    LocalDateTime endOfDay = studyDate.atTime(23, 59, 59);
                    long elapsedSeconds = Duration.between(startTime, endOfDay).getSeconds();
                    this.totalTime += elapsedSeconds;
                    // 초과한 시간은 새로운 타이머에서 setExceedTimeSoFarAfterMidnight() 에서 처리
                    break;
                case STOP:
                    // 그냥 종료. - stop 에서 이미 totalTime 누적함.
                    break;
            }
        }

        // 종료 처리
        this.status = TimerStatus.END;
        startTime = null;
        timeSoFar = 0L;
    }

    public Long getGroupMemberId() {
        return groupMember.getId();
    }
    public Long getUserId() {
        return groupMember.getMember().getId();
    }
    /**
     * 자정 이후의 초과한 공부 시간을 새로운 타이머에 저장
     */
    public void setExceedTimeSoFarAfterMidnight() {

        // 자정 ~ 현재 시각까지의 차이 계산 (초 단위) - front에서 최대 24시간 이내로 처리. 그 이상 시간이라면, 하루(24시간) 씩 짤라서 timer 생성
        long fromMidnightToRequestTime = Duration.between(this.studyDate.atStartOfDay(), LocalDateTime.now()).getSeconds();
        this.timeSoFar = fromMidnightToRequestTime;
        this.totalTime = fromMidnightToRequestTime;
    }

    public TimerDto toEventTimerDto(TimerEvent event) {
        TimerDto timerEventDto = new TimerDto(this);
        switch (event) {
            case START:
                timerEventDto.setEvent(TimerEvent.START);
                timerEventDto.setStatus(TimerStatus.START.toString());
                timerEventDto.setCurrentTimeSoFar(this);
                break;
            case STOP:
                timerEventDto.setEvent(TimerEvent.STOP);
                timerEventDto.setStatus(TimerStatus.STOP.toString());
                break;
            case END:
                timerEventDto.setEvent(TimerEvent.END);
                timerEventDto.setStatus(TimerStatus.END.toString());
                break;
            case DISCONNECT:
                timerEventDto.setEvent(TimerEvent.DISCONNECT);
                break;
            case ENTRY:
                timerEventDto.setEvent(TimerEvent.ENTRY);
                timerEventDto.setStatus(TimerStatus.REST.toString());
                break;
        }
        return timerEventDto;
    }

    public String getNickname() {
        String nickname = groupMember.getNickname();
        if (nickname.isEmpty()){
            log.error("Can not find nickName:Timer.java.getNickName");
            return "NoNickName";
        }
        return groupMember.getNickname();
    }


}

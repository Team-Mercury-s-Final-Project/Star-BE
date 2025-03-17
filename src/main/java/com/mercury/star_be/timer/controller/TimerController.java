package com.mercury.star_be.timer.controller;

import com.mercury.star_be.studygroup.entity.ConnectionStatus;
import com.mercury.star_be.studygroup.service.StudyGroupSseService;
import com.mercury.star_be.timer.dto.EventMessage;
import com.mercury.star_be.timer.dto.TimerDto;
import com.mercury.star_be.timer.dto.TimerEvent;
import com.mercury.star_be.timer.service.TimerService;

import java.util.Objects;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TimerController {

    private final TimerService timerService;
    private final StudyGroupSseService studyGroupSseService;
    private final RedisTemplate<String, Object> redisTemplate;

    // 집중방 입장 했음을 Redis에 저장하고 타이머 정보를 가져오고 GroupMemberId를 Front에 저장.
    // 닉네임 그룹방에 해당하는 거로 조회해서 저장하기.
    @GetMapping("/api/timers/groups/{groupId}/entry")
    public TimerDto enterFocusRoom(@PathVariable Long groupId) {
        return timerService.enterFocusRoom(groupId);
    }

    // 집중방 입장한 사용자들의 타이머 정보를 가져옴
    @GetMapping("/api/timers/groups/{groupId}")
    public Set<TimerDto> getTimerData(@PathVariable Long groupId) {
        return timerService.getFocusRoomTimerDataByGroupIdAndMyUID(groupId);
    }

    // 집중방 클라이언트가 타이머 이벤트를 전송 (START, STOP, END)
    @MessageMapping("/groups/{groupId}/timers")
    @SendTo("/topic/groups.{groupId}.timers")
    public TimerDto handleTimerEvent(@DestinationVariable Long groupId, EventMessage eventMessage) {
        // PK
        Long userId = eventMessage.getUserId(); // For SSE
        Long groupMemberId = eventMessage.getGroupMemberId();// For Timer Event

        return switch (eventMessage.getTimerEvent()) {
            // 이벤트 로직 수행,
            // SSE 유저 상태 메시지 전송.
            // return dto.
            case START -> {
                TimerDto dto = timerService.startMyTimer(groupId, groupMemberId);
                studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.STUDYING);
                yield dto;
            }
            case STOP -> {
                TimerDto dto = timerService.stopTimerByGroupMemberId(groupMemberId);
                studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.RESTING);
                yield dto;
            }
            case END -> {
                TimerDto dto = timerService.endTimerByGroupIdAndUserId(groupId, groupMemberId);
                studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.ONLINE);
                yield dto;
            }
            default -> throw new IllegalArgumentException("Invalid event type: " + eventMessage.getTimerEvent());
        };
    }

}

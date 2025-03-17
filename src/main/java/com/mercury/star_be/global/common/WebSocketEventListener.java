package com.mercury.star_be.global.common;

import com.mercury.star_be.studygroup.entity.ConnectionStatus;
import com.mercury.star_be.studygroup.service.StudyGroupSseService;
import com.mercury.star_be.timer.dto.TimerDto;
import com.mercury.star_be.timer.dto.TimerEvent;
import com.mercury.star_be.timer.service.TimerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final AtomicInteger activeUsers = new AtomicInteger(0);
    private final RedisTemplate<String, String> redisTemplate;
    private final TimerService timerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitMessagingTemplate rabbitTemplate;
    private final StudyGroupSseService studyGroupSseService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        // Entry 체크
        activeUsers.incrementAndGet();
        System.out.println("사용자 연결됨. 현재 연결된 사용자 수: " + activeUsers.get());

        // header 체크
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        // FocusRoom CONNECT 이벤트인 경우
        String roomType = headerAccessor.getFirstNativeHeader("roomType");
        if (roomType != null && roomType.equals("focus")){
            // FocusRoom CONNECT 이벤트 핸들러
            // 접속한 사용자의 정보를 Redis에 저장, 브로드캐스트
            handleFocusRoomConnection(headerAccessor);
        }
    }

    @EventListener
    // 세션 종료 이벤트 (클라이언트가 종료 요청을 보내지 못한 경우 예외처리)
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        activeUsers.decrementAndGet();
        handleTimerDisconnection(event);
    }

    /**
     * Timer DISCONNECT 이벤트 핸들러
     * @param event
     */
    private void handleTimerDisconnection(SessionDisconnectEvent event) {
        // Redis에서 sessionId로 groupId와 userId 조회
        String sessionId = event.getSessionId();
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        Set<String> groupIdAndUserIdAndNickname = setOps.members("focus:"+event.getSessionId());

        // Redis에 세션 존재 시, 집중방 유저 접속 중임
        if (groupIdAndUserIdAndNickname != null && !groupIdAndUserIdAndNickname.isEmpty()){
            String[] split = groupIdAndUserIdAndNickname.iterator().next().split(":");
            long groupId = Long.parseLong(split[0]);
            long userId = Long.parseLong(split[1]);
            long groupMemberId = Long.parseLong(split[2]);
            String nickname = split[3];

            // 타이머 정지 처리
            timerService.stopTimerByGroupMemberId(groupMemberId);

            // Redis에서 삭제
            redisTemplate.opsForSet().remove("focus:"+sessionId,groupId+":"+userId+":"+groupMemberId+":"+nickname);// 세션id 제거
            redisTemplate.opsForSet().remove("focus:" + groupId, groupMemberId + ":" + nickname);


            // Disconnection 브로드캐스트
            TimerDto disconnectEvent = new TimerDto();
            disconnectEvent.setUserId(userId);
            disconnectEvent.setEvent(TimerEvent.DISCONNECT);
            messagingTemplate.convertAndSend(
                    "/topic/groups."+groupId+".timers",
                    disconnectEvent
            );

            // SSE: 집중방 Disconnect 시 현재 인원수 send
            int focusRoomMemberCount = redisTemplate.opsForSet().members("focus:" + groupId).size();
            studyGroupSseService.sendFocusRoomMemberCountToGroup(groupId, focusRoomMemberCount);
            // SSE: 집중방 Disconnect 시 접속중 상태 send
            studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.ONLINE);
        }
    }

    /**
     * FocusRoom CONNECT 이벤트 핸들러
     * @param headerAccessor
     */
    private void handleFocusRoomConnection(StompHeaderAccessor headerAccessor) {
        // groupId : 집중방 구분.
        String groupId = headerAccessor.getFirstNativeHeader("groupId");

        // groupId: 집중방 특정 접속자, 타이머 stop을 위해 저장.
        String groupMemberId = headerAccessor.getFirstNativeHeader("groupMemberId");

        // userId: Disconnect 시, SSE 구분
        String userId = headerAccessor.getFirstNativeHeader("userId");

        // nickname redis 캐시해놓으면 groupMember join 필요 없음.
        String nickname = headerAccessor.getFirstNativeHeader("nickname");

        // focus 방일 경우만 처리 - focus 방은 참여 인원 Redis에 저장
        // disconnect 용 세션id 필요. 하지만 sid로 접속 유저 체크 불가.
        if (userId != null && groupMemberId != null) {
            String redisKey = "focus:" + groupId;
            SetOperations<String, String> setOps = redisTemplate.opsForSet();

            // 세션 id 저장 (세션 종료 시, Redis에서 제거하고 timer도 종료 : groupId, userId로 조회)
            String sessionId = headerAccessor.getSessionId();
            if (sessionId != null) {
                setOps.add("focus:"+sessionId, groupId+":"+userId+":"+groupMemberId+":"+nickname);
                Boolean expire = redisTemplate.expire("focus:"+sessionId, Duration.ofDays(1));
            }else {
                // 세션 아이디가 없을 경우 예외 처리
                log.error("SessionId is null");
                throw new RuntimeException("SessionId is null");
            }

            // Redis에 유저 추가
            setOps.add(redisKey, groupMemberId+":"+nickname);

            // TTL 설정
            redisTemplate.expire(redisKey, Duration.ofDays(1));

            // Entry 이벤트 브로드캐스트 객체
            // 가장 최신 timer 객체 불러오기
            TimerDto entryEvent = timerService.getMyRecentTimerByGroupMemberId(Long.parseLong(groupMemberId));

            // 타이머가 없는 경우, 새로 입장한 사용자임. Entry 이벤트 객체 생성
            if (entryEvent == null) {
                entryEvent = new TimerDto();
                entryEvent.setEvent(TimerEvent.ENTRY);
                entryEvent.setUserId(Long.parseLong(userId));
                entryEvent.setGroupMemberId(Long.parseLong(groupMemberId));
                entryEvent.setNickname(nickname);
                entryEvent.setTimeSoFar(0);
                entryEvent.setStatus("REST");
            }
            // 타이머 있는 경우
            entryEvent.setNickname(nickname);

            // Entry 이벤트 브로드캐스트
            messagingTemplate.convertAndSend("/topic/groups."+groupId+".timers", entryEvent);

            // SSE: 집중방 입장 시 현재 인원수 send
            int focusRoomMemberCount = redisTemplate.opsForSet().members("focus:" + groupId).size();
            studyGroupSseService.sendFocusRoomMemberCountToGroup(Long.parseLong(groupId), focusRoomMemberCount);
        }
    }
}

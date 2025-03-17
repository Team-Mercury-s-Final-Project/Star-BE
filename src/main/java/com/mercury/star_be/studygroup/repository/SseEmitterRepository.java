package com.mercury.star_be.studygroup.repository;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.code.StudyGroupErrorCode;
import com.mercury.star_be.studygroup.entity.ConnectionStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SseEmitterRepository {

	private final Map<Long, Map<Long, SseEmitter>> sseEmittersMap = new ConcurrentHashMap<>();
	private final RedisTemplate<String, Object> redisTemplate;

	private static final String GROUP_PREFIX = "study_group";

	public void save(Long groupId, Long userId, SseEmitter sseEmitter) {
		Map<Long, SseEmitter> groupSseEmitters = sseEmittersMap.computeIfAbsent(groupId,
			l -> new ConcurrentHashMap<>());

		SseEmitter existingEmitter = groupSseEmitters.get(userId);
		if (existingEmitter != null) {
			existingEmitter.complete();
		}
		groupSseEmitters.put(userId, sseEmitter);
	}

	public void updateStatus(Long groupId, Long userId, ConnectionStatus status) {
		redisTemplate.opsForHash().put(GROUP_PREFIX + groupId, userId.toString(), status.toString());
	}

	public void delete(Long groupId, Long userId, SseEmitter sseEmitter) {
		Map<Long, SseEmitter> groupSseEmitters = sseEmittersMap.getOrDefault(groupId, new ConcurrentHashMap<>());
		SseEmitter existingSseEmitter = groupSseEmitters.get(userId);
		if (existingSseEmitter.equals(sseEmitter)) {
			groupSseEmitters.remove(userId);
			if (groupSseEmitters.isEmpty()) sseEmittersMap.remove(groupId);
		}
		redisTemplate.opsForHash().delete(GROUP_PREFIX + groupId, userId.toString());
	}

	public Map<Object, Object> getConnectedUsers(Long groupId) {
		return redisTemplate.opsForHash().entries(GROUP_PREFIX + groupId);
	}

	public Map<Long, SseEmitter> findAllByGroupId(Long groupId) {
		return sseEmittersMap.getOrDefault(groupId, new ConcurrentHashMap<>());
	}

	public int getFocusRoomMemberCount(Long groupId) {
		return redisTemplate.opsForSet().members("focus:" + groupId).size();
	}

	public int getChatRoomMemberCount(Long chatRoomId) {
		return redisTemplate.opsForSet().members("chatRoom" + chatRoomId + ":connectedUsers").size();
	}

	@Scheduled(fixedRate = 30 * 1000)	// 30초
	public void sendHeartbeat() {
		for (Map.Entry<Long, Map<Long, SseEmitter>> groupEntry : sseEmittersMap.entrySet()) {
			Map<Long, SseEmitter> groupSseEmitters = groupEntry.getValue();
			for (Map.Entry<Long, SseEmitter> userEntry : groupSseEmitters.entrySet()) {
				SseEmitter emitter = userEntry.getValue();
				try {
					emitter.send(SseEmitter.event()
						.name("heartbeat")
						.data("ping"));
				} catch (IOException e) {
					throw new BusinessException(StudyGroupErrorCode.SSE_EMITTER_IO_EXCEPTION);
				}
			}
		}
	}
}

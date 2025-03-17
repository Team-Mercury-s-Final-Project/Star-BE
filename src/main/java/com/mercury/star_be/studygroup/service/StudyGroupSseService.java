package com.mercury.star_be.studygroup.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mercury.star_be.studygroup.entity.ConnectionStatus;

public interface StudyGroupSseService {

	SseEmitter subscribe(Long groupId, Long userId);

	void sendFocusRoomMemberCountToGroup(Long groupId, int memberCount);

	void sendChatRoomMemberCountToGroup(Long groupId, int memberCount);

	void sendMemberStatusToGroup(Long groupId, Long userId, ConnectionStatus status);

	void sendGroupMemberInfoToGroup(Long groupId);

	void sendToGroup(Long groupId, String eventName, Object data);
}

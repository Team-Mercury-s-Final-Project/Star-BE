package com.mercury.star_be.studygroup.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.mercury.star_be.studygroup.dto.SseQueueMessage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SseRabbitMQListener {

	private final StudyGroupSseService studyGroupSseService;

	/**
	 * sse 큐에서 메시지를 가져와 그룹에 연결된 사용자에게 전송
	 * 서버마다 큐 이름이 다르기 때문에 #{sseQueue.name}으로 가져옴
	 */
	@RabbitListener(queues = "#{sseQueue.name}")
	public void receiveSseMessage(SseQueueMessage message) {
		studyGroupSseService.sendToGroup(message.getGroupId(), message.getEventName(), message.getData());
	}
}

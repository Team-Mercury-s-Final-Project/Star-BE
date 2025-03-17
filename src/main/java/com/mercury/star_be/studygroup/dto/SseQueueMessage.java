package com.mercury.star_be.studygroup.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SseQueueMessage {

	private Long groupId;
	private String eventName;
	private Object data;
}

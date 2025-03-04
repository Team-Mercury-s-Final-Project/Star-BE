package com.mercury.star_be.chat.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ChatRabbitPayload {
    private Long chatRoomId;
    private Object object;
}

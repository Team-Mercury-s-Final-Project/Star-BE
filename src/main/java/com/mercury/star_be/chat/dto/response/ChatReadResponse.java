package com.mercury.star_be.chat.dto.response;

import com.mercury.star_be.chat.dto.common.ChatEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
/**채팅방 내 채팅 읽은 인원 확인용 response dto*/
@Getter
@Builder
@AllArgsConstructor
public class ChatReadResponse {
    private Long chatMessageId;
    private int unreadCount;
    private ChatEvent event;
}

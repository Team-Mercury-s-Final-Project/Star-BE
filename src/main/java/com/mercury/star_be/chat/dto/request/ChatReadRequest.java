package com.mercury.star_be.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/**채팅방 내 채팅 읽은 인원 확인용 request dto*/
@Getter
@Builder
@AllArgsConstructor
public class ChatReadRequest {
    @NotBlank
    private Long chatMessageId;
    @NotBlank
    private Long chatUserId;
    @NotBlank
    private Set<String> connectedUsers;
}

package com.mercury.star_be.chat.dto.common;

import com.mercury.star_be.chat.entity.ChatRoomType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**채팅목록에서 채팅방 정보를 보여줄 때 사용하는 DTO*/
@Getter
@Builder
@AllArgsConstructor
public class ChatRoomDto {
    private Long id;
    @Enumerated(EnumType.STRING)
    private ChatRoomType chatRoomType;
    private Long groupId;
    private List<Long> unreadMessages; //채팅방에서 읽지 않은 메시지들 id list
    private ChatRecentMessageDto recentMessage;
    private String chatRoomName;
    private String chatRoomImage;
}

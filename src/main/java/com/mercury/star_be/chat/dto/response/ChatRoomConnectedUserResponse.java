package com.mercury.star_be.chat.dto.response;

import com.mercury.star_be.chat.dto.common.ChatEvent;
import com.mercury.star_be.chat.dto.request.ChatRoomConnectedUserRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**현재 채팅방에 접속한 사용자 정보 response 객체*/
@Getter
@Builder
@AllArgsConstructor
public class ChatRoomConnectedUserResponse {

    private Set<String> connectedMemberIds = new HashSet<>();
    private ChatEvent event;
}

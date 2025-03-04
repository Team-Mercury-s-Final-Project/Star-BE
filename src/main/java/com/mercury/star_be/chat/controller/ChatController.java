package com.mercury.star_be.chat.controller;

import com.mercury.star_be.chat.dto.request.*;
import com.mercury.star_be.chat.dto.response.*;
import com.mercury.star_be.chat.entity.ChatRoom;
import com.mercury.star_be.chat.service.ChatService;
import com.mercury.star_be.global.common.ApiResponse;
import com.mercury.star_be.user.dto.response.UserResponse;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    /**
     * 채팅방 조회 컨트롤러
     * */
    @GetMapping("/api/chats/{chatRoomId}")
    public ApiResponse<ChatRoomResponse> getChatRoom(
            @PathVariable(value = "chatRoomId") Long chatRoomId
    ) {
        //채팅방에 소속된 인원인지 확인.
        UserResponse userResponse =
                (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        chatService.isJoinedChatRoom(userResponse.getId(),chatRoomId);
        //채팅방 조회
        ChatRoomResponse chatRoomResponse = chatService.getChatRoom(chatRoomId);
        return ApiResponse.success(chatRoomResponse);
    }


    /**
     * 스터디그룹에서 채팅방으로 이동
     * - 내 아이디, 그룹아이디 등으로 채팅방아이디를 찾고,
     * - 채팅방아이디로 내가 읽지 않은 모든 메시지들 찾음
     * - 읽지 않은 메시지가 있다면, unreadCount -1 / 채팅 읽음 테이블에 insert
     * - 채팅방쪽에 그룹채팅변화체크 큐 하나 구독
     * - 메시지를 받아 반영
     * */
    @PostMapping("/api/chat/updateGroupUnreadMessages/{groupId}")
    public ApiResponse<Long> updateGroupUnreadMessages(
        @PathVariable Long groupId
    ){
        //유저정보
        UserResponse userResponse =
                (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        //채팅방정보
        ChatRoom chatRoom = chatService.findByGroupId(groupId);
        chatService.updateGroupUnreadMessages(userResponse.getId(), chatRoom.getId(), groupId);
        return ApiResponse.success(chatRoom.getId());
    }

    /**
     * DM에서 채팅방으로 이동 시 사용
     * - 채팅방아이디로 내가 읽지 않은 모든 메시지들 찾음
     * - 읽지 않은 메시지가 있다면, unreadCount -1 / 채팅 읽음 테이블에 insert
     * - 채팅방쪽에 그룹채팅변화체크 큐 하나 구독
     * - 메시지를 받아 반영
     * */
    @PostMapping("/api/chat/updateGroupUnreadMessagesForDM/{chatRoomId}")
    public ApiResponse<String> updateGroupUnreadMessagesForDM(
            @PathVariable Long chatRoomId
    ){
        //유저정보
        UserResponse userResponse =
                (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        //채팅방정보
        chatService.updateGroupUnreadMessagesForDM(userResponse.getId(), chatRoomId);
        return ApiResponse.success("success");
    }

    /**
     * 채팅 메시지 전송 컨트롤러(일반 텍스트)
     * /pub/chat/sendTextMessage/{chatRoomId} 경로로 보낸 메시지를 받음
     * 이 컨트롤러에서 처리된 메시지를 /sub/chat/{chatRoomId} 경로로 구독하고 있는 클라이언트에게 전송
     * */
    @MessageMapping("/chat/sendTextMessage/{chatRoomId}")
    @SendTo("/topic/chat.{chatRoomId}")
    public ApiResponse<ChatMessageResponse> sendChatMessage(
            @Payload ChatMessageRequest chatMessageRequest
    ){
        ChatMessageResponse chatMessageResponse = chatService.sendMessage(chatMessageRequest);
        return ApiResponse.success(chatMessageResponse);
    }

    /**채팅 메시지 전송 컨트롤러(사진 파일)*/
    @MessageMapping("/chat/sendFile/{chatRoomId}")
    @SendTo("/topic/chat.{chatRoomId}")
    public ApiResponse<ChatMessageResponse> uploadChatFile(
            @Payload ChatMessageRequest chatMessageRequest
    ){
        ChatMessageResponse chatMessageResponse = chatService.sendMessage(chatMessageRequest);
        return ApiResponse.success(chatMessageResponse);
    }

    /**
     * 채팅목록으로 최신 메시지 전달 컨트롤러
     * 각 채팅방의 최신 메시지를 현재 사용자에게 전달
     * */
    @MessageMapping("/chat/sendRecentMessageToChatList/{chatRoomId}")
    public void sendRecentMessageToChatList(
            @Payload ChatRecentMessageRequest chatRecentMessageRequest,
            Authentication auth
    ){
        Long userId = JwtUtil.getAuthenticatedUser(auth).getId();
        ChatRecentMessageResponse response = chatService.sendRecentMessageToChatList(chatRecentMessageRequest);
        //내가 그 방을 구독하고 있는지 체크해야하는가?
        messagingTemplate.convertAndSend("/topic/chatList." + userId, response);
    }

    /**채팅방 내 메시지 읽음 udpate 컨트롤러*/
    @MessageMapping("/readCheck/{chatRoomId}")
    @SendTo("/topic/chat.{chatRoomId}")
    public ApiResponse<ChatReadResponse> updateReadUsers(
            @DestinationVariable
            Long chatRoomId,
            @Payload ChatReadRequest chatReadRequest
    ){
        ChatReadResponse response = chatService.updateReadCount(chatReadRequest, chatRoomId);
        return ApiResponse.success(response);
    }
    /**현재 접속한 사용자 반환*/
    @MessageMapping("/chat/connect/{chatRoomId}")
    @SendTo("/topic/chat.{chatRoomId}")
    public ApiResponse<ChatRoomConnectedUserResponse> chatRoomConnect(
            @DestinationVariable Long chatRoomId,
            @Payload ChatRoomConnectedUserRequest request
    ){
        ChatRoomConnectedUserResponse response = chatService.insertChatRoomConnectedUsers(request, chatRoomId);
        return ApiResponse.success(response);
    }
    /**현재 접속 해제한 사용자 반환*/
    @MessageMapping("/chat/disconnect/{chatRoomId}")
    @SendTo("/topic/chat.{chatRoomId}")
    public ApiResponse<ChatRoomConnectedUserResponse> chatRoomDisconnect(
            @DestinationVariable Long chatRoomId,
            @Payload ChatRoomConnectedUserRequest request
    ){
        ChatRoomConnectedUserResponse response = chatService.removeChatRoomConnectedUsers(request, chatRoomId);
        return ApiResponse.success(response);
    }

    /**내 채팅방 목록 조회 컨트롤러*/
    @GetMapping("/api/users/{userId}/chats")
    public ApiResponse<ChatRoomListResponse> getChatRoomList(
            @PathVariable
            Long userId
    ){
        ChatRoomListResponse chatRoomListResponse = chatService.getUserChatRooms(userId);
        return ApiResponse.success(chatRoomListResponse);
    }

    /**
     * 두 사용자 간 1:1채팅방 아이디 조회 컨트롤러
     * 두 사용자의 아이디를 받아 1:1채팅방 아이디를 return
     * 두 사용자 간의 채팅 기록이 없을 때 사용
     * */
    @GetMapping("/api/chat/findDmChatRoomId")
    public ApiResponse<Long> findDmChatRoomId(
            @RequestParam Long senderId,
            @RequestParam Long receiverId
    ){
        Long chatRoomId = chatService.findChatRoomIdByUserIds(senderId, receiverId);
        return ApiResponse.success(chatRoomId);
    }

    /**
     * 두 사용자 간 1:1채팅방 아이디 조회 컨트롤러
     * 두 사용자의 아이디를 받아 1:1채팅방 아이디를 return
     * 두 사용자 간의 채팅 기록이 존재할 때 사용
     * */
    @GetMapping("/api/chat/findExistingChatRoomId")
    public ApiResponse<Long> findExistingChatRoomId(
            @RequestParam Long senderId,
            @RequestParam Long receiverId
    ){
        Long chatRoomId = chatService.findExistingChatRoomId(senderId, receiverId);
        return ApiResponse.success(chatRoomId);
    }



    /**
     * 1:1 채팅 사용자 간의 이전 채팅 메시지 숫자 확인 컨트롤러
     * */
    @GetMapping("/api/chat/chatMessageCountCk")
    public ApiResponse<ChatMessageCountCkResponse> chatMessageCountCk(
        @RequestParam Long senderId,
        @RequestParam Long receiverId
    ){
        ChatMessageCountCkResponse chatMessageCountCkResponse
                = chatService.findChatMessageRecord(senderId, receiverId);
        return ApiResponse.success(chatMessageCountCkResponse);
    }

    @GetMapping("/api/chat/{groupId}/chatMessageCountCk")
    public ApiResponse<ChatMessageCountCkResponse> chatMessageCountCk(
            @PathVariable Long groupId
    ){
        ChatMessageCountCkResponse chatMessageCountCkResponse
                = chatService.findChatMessageRecordForGroup(groupId);
        return ApiResponse.success(chatMessageCountCkResponse);
    }

    /**1:1 채팅방 개설 컨트롤러*/
    @PostMapping("/api/chat/createDMChatRoom")
    public ApiResponse<CreateDmChatRoomResponse> createDMChatRoom(
            @RequestBody CreateChatRoomRequest createChatRoomRequest
    ){
        CreateDmChatRoomResponse response = chatService.createDMChatRoom(createChatRoomRequest);
        return ApiResponse.success(response);
    }

    /**그룹 채팅방 개설 컨트롤러*/
    @PostMapping("/api/chat/createGroupChatRoom")
    public ApiResponse<CreateGroupChatRoomResponse> createGroupChatRoom(
            @RequestBody CreateChatRoomRequest createChatRoomRequest
    ){
        chatService.createGroupChatRoom(createChatRoomRequest);
        CreateGroupChatRoomResponse createChatRoomResponse = CreateGroupChatRoomResponse.builder()
                .result("그룹 채팅방이 생성되었습니다.")
                .build();
        return ApiResponse.success(createChatRoomResponse);
    }

    /**
     * 채팅방 가입 컨트롤러
     * 그룹아이디를 path로 받아와 가입
     * */
    @PostMapping("/api/chat/joinChatRoom/{groupId}")
    public ApiResponse<ChatRoomJoinResponse> joinChatRoom(
            @PathVariable Long groupId
    ){
        ChatRoomJoinResponse chatRoomJoinResponse = chatService.joinChatRoom(groupId);
        return ApiResponse.success(chatRoomJoinResponse);
    }

    /**한 채팅방의 한 유저가 읽지 않은 모든 메시지 읽음 처리 컨트롤러*/
    @PostMapping("/api/chats/{chatRoomId}/insertAllUnreadChatMessages")
    public ApiResponse<String> insertAllUnreadChatMessages(
            @PathVariable Long chatRoomId,
            @RequestBody
            ChatUpdateReadMessagesRequest chatUpdateReadMessagesRequest
    ){
        UserResponse userResponse =
                (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        chatService.updateAndInsertChatReads(chatUpdateReadMessagesRequest, userResponse.getId(), chatRoomId);
        return ApiResponse.success("success");
    }

    @PostMapping("/api/chats/{chatRoomId}/insertUnreadMessagesToChatRead")
    public ApiResponse<String> insertUnreadMessagesToChatRead(
            @PathVariable Long chatRoomId
    ){
        chatService.insertUnreadMessagesToChatRead(chatRoomId);
        return ApiResponse.success("success");
    }

    /**사용자가 이 메시지를 읽었는지 체크하는 컨트롤러*/
    @PostMapping("/api/chats/isReadCheck")
    public ApiResponse<Boolean> isReadCheck(
            @RequestBody
            ChatReadRequest chatReadRequest
    ){
        boolean isReadCheck = chatService.isReadCheck(chatReadRequest);
        return ApiResponse.success(isReadCheck);
    }

    //사용자 차단
    //사용자 차단 해제
    //차단 사용자 목록

}

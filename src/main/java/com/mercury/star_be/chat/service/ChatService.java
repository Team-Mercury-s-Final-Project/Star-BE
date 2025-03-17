package com.mercury.star_be.chat.service;

import com.mercury.star_be.chat.dto.common.ChatRecentMessageDto;
import com.mercury.star_be.chat.dto.common.ChatRoomDto;
import com.mercury.star_be.chat.dto.common.ChatRoomMemberDto;
import com.mercury.star_be.chat.dto.request.*;
import com.mercury.star_be.chat.dto.response.*;
import com.mercury.star_be.chat.entity.ChatMessage;
import com.mercury.star_be.chat.entity.ChatRoom;

import java.util.List;

public interface ChatService {
    //채팅방 조회
    ChatRoom findByChatRoomId(Long chatRoomId);
    ChatRoomResponse getChatRoom(Long chatRoomId);
    //그룹아이디로 채팅방 조회
    ChatRoom findByGroupId(Long groupId);
    //채팅방 생성(그룹채팅방이면 그룹원들 id / DM이면 상대방 id가 필요)
    CreateDmChatRoomResponse createDMChatRoom(CreateChatRoomRequest createChatRoomRequest);
    void createGroupChatRoom(CreateChatRoomRequest createChatRoomRequest);
    //채팅메시지 전송
    ChatMessageResponse sendMessage(ChatMessageRequest chatMessageRequest);
    //사용자 채팅목록 조회
    ChatRoomListResponse getUserChatRooms(Long userId);
    //채팅방 메시지 가져오기
    List<ChatMessage> findChatRoomMessages(Long chatRoomId);
    //최신메시지 가져오기
    ChatRecentMessageDto findRecentMessage(Long chatRoomId, Long userId);
    //채팅방 entity -> dto로 변환
    ChatRoomDto fromChatRoomEntity(ChatRoom chatRoom, Long userId);
    //1:1채팅에서 두 사용자 간의 이전 채팅 기록 count 확인
    ChatMessageCountCkResponse findChatMessageRecord(Long senderId, Long receiverId);
    ChatMessageCountCkResponse findChatMessageRecordForGroup(Long groupId);
    //그룹채팅 가입
    ChatRoomJoinResponse joinChatRoom(Long groupId, Long userId);
    //사용자 채팅방 조회(사용자 아이디, 채팅방 아이디)
    boolean isJoinedChatRoom(Long chatUserId, Long chatRoomId);
    //채팅방 id를 받아 List<ChatRoomMemberDto>로 return
    List<ChatRoomMemberDto> getChatRoomMembers(ChatRoom chatRoom);
    //읽음 update
    ChatReadResponse updateReadCount(ChatReadRequest chatReadRequest, Long chatRoomId);
    //사용자가 채팅방에서 읽지 않은 메시지들의 아이디 리스트
    List<Long> findUnreadMessageIds(Long chatRoomId, Long userId);
    //읽지 않은 메시지들의 아이디 리스트를 받아  메시지 읽음 테이블에 insert / 메시지 테이블에 update
    void updateAndInsertChatReads(ChatUpdateReadMessagesRequest request, Long userId, Long chatRoomId);
    //읽지 않은 메시지들의 아이디 리스트를 받아 메시지 읽음 테이블에 한번에 insert
    void insertChatReads(ChatUpdateReadMessagesRequest chatUpdateReadMessagesRequest, Long userId);
    //읽지 않은 메시지들의 아이디 리스트를 받아 메시지 테이블의 unreadCount 한번에 update
    void updateChatReads(ChatUpdateReadMessagesRequest request);
    //유저아이디, 채팅방아이디, 그룹아이디를 받아 해당 사용자의 해당 그룹 읽지 않은 메시지들 전부 읽음 처리
    void updateGroupUnreadMessages(Long userId, Long chatRoomId, Long groupId);
    //유저아이디와 채팅방 아이디를 받아 해당 사용자의 해당 그룹 읽지 않은 메시지들 전부 읽음 처리
    void updateGroupUnreadMessagesForDM(Long userId, Long chatRoomId);
    //두 사용자의 아이디를 받아 1:1채팅방 아이디를 return(채팅기록이 없을 때)
    Long findChatRoomIdByUserIds(Long senderId, Long receiverId);
    //두 사용자의 아이디를 받아 1:1채팅방 아이디를 return(채팅기록이 존재할 때)
    Long findExistingChatRoomId(Long senderId, Long receiverId);
    //사용자가 채팅방에서 읽지 않은 메시지들을 모두 읽음처리
    void insertUnreadMessagesToChatRead(Long chatRoomId);
    //채팅방 접속 유저 추가
    ChatRoomConnectedUserResponse insertChatRoomConnectedUsers(ChatRoomConnectedUserRequest request, Long chatRoomId);
    //채팅방 접속 유저 삭제
    ChatRoomConnectedUserResponse removeChatRoomConnectedUsers(ChatRoomConnectedUserRequest request, Long chatRoomId);
    //채팅목록으로 채팅방의 최신 메시지 전달
    ChatRecentMessageResponse sendRecentMessageToChatList(ChatRecentMessageRequest request);
    boolean isReadCheck(ChatReadRequest request);
    //사용자 채팅방 삭제
    public void deleteUSerChatRoom(Long groupId, Long userId);
}

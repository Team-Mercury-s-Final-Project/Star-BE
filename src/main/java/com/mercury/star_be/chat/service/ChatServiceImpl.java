package com.mercury.star_be.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercury.star_be.chat.dto.common.*;
import com.mercury.star_be.chat.dto.request.*;
import com.mercury.star_be.chat.dto.response.*;
import com.mercury.star_be.chat.entity.*;
import com.mercury.star_be.chat.repository.*;
import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.code.ChatErrorCode;
import com.mercury.star_be.global.error.code.StudyGroupErrorCode;
import com.mercury.star_be.global.error.code.UserErrorCode;
import com.mercury.star_be.studygroup.entity.ConnectionStatus;
import com.mercury.star_be.studygroup.entity.GroupMember;
import com.mercury.star_be.studygroup.entity.StudyGroup;
import com.mercury.star_be.studygroup.repository.StudyGroupRepository;
import com.mercury.star_be.studygroup.service.StudyGroupSseService;
import com.mercury.star_be.user.dto.response.UserResponse;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.mercury.star_be.global.config.RabbitMQConfig.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final UserRepository userRepository;
    private final RabbitMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ChatMessageFileRepository chatMessageFileRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final ChatReadRepository chatReadRepository;
    private final ChatCustomRepository chatCustomRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final StudyGroupSseService studyGroupSseService;


    /**
     * 채팅방 조회
     */
    @Override
    public ChatRoom findByChatRoomId(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId).orElseThrow(
                () -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
        );
    }

    /**
     * 채팅룸 정보를 조회하여 ChatRoomResponse로 return
     */
    @Override
    public ChatRoomResponse getChatRoom(Long chatRoomId) {
        ChatRoom chatRoom = findByChatRoomId(chatRoomId);
        Long studyGroupId = null;
        if (chatRoom.getChatRoomType().equals(ChatRoomType.GROUP)) {
            studyGroupId = chatRoom.getStudyGroup().getId();
        }

        ChatRoomResponse chatRoomResponse = ChatRoomResponse.builder()
        //채팅방 멤버 (DM은 발신자 수신자 / 그룹은 그룹멤버들)
            .chatMembers(getChatRoomMembers(chatRoom))
            .messages(getChatRoomMessageDtos(chatRoomId))
            .chatRoomType(chatRoom.getChatRoomType())
            .studyGroupId(studyGroupId)
            .build();
        return chatRoomResponse;
    }

    /**
     * 채팅방 이름 입력 서비스
     * DM : 간단하게 상대방 닉네임
     * GROUP : 간단하게 스터디그룹명
     * */
    //public
    
    /**그룽아이디로 채팅방 조회*/
    @Override
    public ChatRoom findByGroupId(Long groupId) {
        return chatRoomRepository.findByStudyGroupId(groupId).orElseThrow(
                () -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)
        );
    }

    /**
     * 채팅방 id를 받아
     * List<ChatRoomMessageDto>로 return
     */
    public List<ChatRoomMessageDto> getChatRoomMessageDtos(Long chatRoomId) {
        List<ChatMessage> chatMessages = chatMessageRepository.findByChatRoomId(chatRoomId).orElse(List.of());

        return chatMessages.stream()
                .map(chatMessage -> ChatRoomMessageDto.builder()
                        .id(chatMessage.getId())
                        .senderId(chatMessage.getChatSender().getId())
                        .nickName(chatMessage.getChatSender().getNickname())
                        .content(chatMessage.getContent())
                        .unreadCount(chatMessage.getUnreadCount())
                        .createdAt(chatMessage.getCreatedAt())
                        .profileImgUrl(chatMessage.getChatSender().getImage())
                        .messageFiles(chatMessage.getContent() == null ?
                                (!chatMessage.getChatMessageFiles().isEmpty() ?
                                        chatMessage.getChatMessageFiles().stream()
                                                .map(this::convertToFileDto)
                                                .collect(Collectors.toList())
                                        : null)
                                : null)
                        .build())
                .collect(Collectors.toList());
    }

    private ChatMessageFileDto convertToFileDto(ChatMessageFile chatMessageFile) {
        return ChatMessageFileDto.builder()
                .id(chatMessageFile.getId())
                .fileUrl(chatMessageFile.getFileUrl())
                .fileType(chatMessageFile.getFileType())
                .chatMessageId(chatMessageFile.getChatMessage().getId())
                .build();
    }



    /**
     * List<ChatMessageFile> chatMessageFiles를 받아
     * List<ChatMessageFileDto>로 return
     */
    public List<ChatMessageFileDto> getChatMessageFileDtos(List<ChatMessageFile> chatMessageFiles) {
        List<ChatMessageFileDto> chatMessageFileDtos = new ArrayList<>();
        for (ChatMessageFile chatMessageFile : chatMessageFiles) {
            ChatMessageFileDto chatMessageFileDto = ChatMessageFileDto.builder()
                    .id(chatMessageFile.getId())
                    .fileUrl(chatMessageFile.getFileUrl())
                    .fileType(chatMessageFile.getFileType())
                    .chatMessageId(chatMessageFile.getId())
                    .build();
            chatMessageFileDtos.add(chatMessageFileDto);
        }
        return chatMessageFileDtos;
    }

    /**
     * 채팅 전송 서비스
     * 일반 채팅 메시지 / 파일 업로드
     */
    @Override
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest chatMessageRequest) {
        // Redis에 채팅 데이터 저장

        // 채팅방 찾기
        ChatRoom chatRoom = findByChatRoomId(chatMessageRequest.getChatRoomId());

        // 송신자 조회
        User chatSender = userRepository.findById(chatMessageRequest.getSenderId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));

        // 읽지 않은 사람 수
        int unreadCount = 2;
        if (chatRoom.getChatRoomType() == ChatRoomType.GROUP) {
            //메시지 송신자 제외
            unreadCount = chatRoom.getStudyGroup().getMemberCount();
        }

        // 메시지 객체 생성
        ChatMessage chatMessage = ChatMessage.builder()
                .content(chatMessageRequest.getMessageContent())
                .unreadCount(unreadCount)
                .createdAt(LocalDateTime.now())
                .chatSender(chatSender)
                .chatReceiver(null)
                .chatRoom(chatRoom)
                .build();

        // DM일 경우 수신자 추가
// DM일 경우 수신자 추가
        if (chatMessageRequest.getChatRoomType().equals(ChatRoomType.DM)) {
            // receiver가 없다면...
            Long tempReceiverId = 0L;
            if (chatMessageRequest.getReceiverId() == null) {
                List<ChatRoomMemberDto> dmChatRoomMembers = getChatRoomMembers(chatRoom);
                System.out.println("채팅방멤버: " + dmChatRoomMembers);
                for (ChatRoomMemberDto chatRoomMemberDto : dmChatRoomMembers) {
                    if (!chatRoomMemberDto.getId().equals(chatMessageRequest.getSenderId())) {
                        tempReceiverId = chatRoomMemberDto.getId();
                        System.out.println("상대방 아이디: " + tempReceiverId);
                    }
                }
                User chatReceiver = userRepository.findById(tempReceiverId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));
                System.out.println("상대방: " + chatReceiver);
                chatMessage.updateReceiver(chatReceiver);
            } else {
                User chatReceiver = userRepository.findById(chatMessageRequest.getReceiverId())
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));
                chatMessage.updateReceiver(chatReceiver);
            }
        }

        // 메시지 파일 객체 생성 및 저장
        List<ChatMessageFile> chatMessageFiles = new ArrayList<>();
        //파일들 url을 저장시킬 변수
        StringBuilder fileUrls = new StringBuilder();
        if (chatMessageRequest.getMessageFiles() != null && !chatMessageRequest.getMessageFiles().isEmpty()) {
            
            for (ChatMessageFileDto dto : chatMessageRequest.getMessageFiles()) {
                fileUrls.append(dto.getFileUrl()).append(" ");
                ChatMessageFile chatMessageFile = ChatMessageFile.builder()
                        .fileUrl(dto.getFileUrl())
                        .fileType(dto.getFileType())
                        .chatMessage(chatMessage)
                        .build();
                chatMessageFiles.add(chatMessageFile);
                chatMessageFileRepository.save(chatMessageFile);
            }
            //파일url로 메시지 내용 변경
            chatMessage.fileUploadContentString(fileUrls.toString());
            chatMessageRequest.fileUploadContentString(fileUrls.toString());
        }
        // 파일 업데이트 및 저장
        chatMessage.updateFiles(chatMessageFiles);
        ChatMessage newChatMessage =  chatMessageRepository.save(chatMessage);

        // ChatMessageResponse 생성
        ChatMessageResponse response = ChatMessageResponse.builder()
                .id(newChatMessage.getId())
                .nickName(chatMessageRequest.getNickName())
                .createdAt(LocalDateTime.now())
                .unreadCount(unreadCount) // 읽지 않은 메시지 수 (기본값 : 채팅방 인원)
                .messageContent(newChatMessage.getContent())
                .profileImgUrl(chatSender.getImage())
                .senderId(chatSender.getId())
                .messageFiles(chatMessageRequest.getMessageFiles()) // 파일 정보 추가
                .build();

        //채팅목록으로 새로운 메시지 전달
        try {
            ChatRecentMessageDto dto = ChatRecentMessageDto.builder()
                    .id(response.getId())
                    .nickName(response.getNickName())
                    .profileImgUrl(response.getProfileImgUrl())
                    .content(response.getMessageContent())
                    .createdAt(response.getCreatedAt())
                    .userId(chatSender.getId())
                    .build();

            String messageJson = objectMapper.writeValueAsString(dto);
            messagingTemplate
                    .convertAndSend(
                            CHAT_EXCHANGE_NAME,
                            CHAT_RECENT_MESSAGE_ROUTING_KEY + chatSender.getId(),
                            messageJson
                    );
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
        } catch (AmqpException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
        }
        return response;
    }

    /**
     * 채팅방 생성 서비스
     * 1:1 채팅방 생성 : sender, receiver 둘 다 저장
     * 그룹채팅방 개설 : sender만 저장
     */
    @Override
    @Transactional
    public CreateDmChatRoomResponse createDMChatRoom(CreateChatRoomRequest createChatRoomRequest) {
        //송신자
        User sender =
                userRepository.findById(createChatRoomRequest.getSenderId()).orElseThrow(
                        () -> new RuntimeException("송신자를 찾지 못합니다.")
                );

        //수신자 정보 가져오기
        User receiver =
                userRepository.findById(createChatRoomRequest.getReceiverId()).orElseThrow(
                        () -> new RuntimeException("수신자를 찾지 못합니다.")
                );

        // 채팅방 새로 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .chatRoomType(ChatRoomType.DM)
                .createdAt(LocalDateTime.now())
                .studyGroup(null)
                .build();

        // 사용자 채팅방 생성(송신자 / 수신자)
        UserChatRoom senderUserChatRoom = UserChatRoom.builder()
                .joinedAt(LocalDateTime.now())
                .isBlock(false)
                .chatRoom(chatRoom)
                .chatUser(sender)
                .build();
        
        UserChatRoom receiverUserChatRoom = UserChatRoom.builder()
                .joinedAt(LocalDateTime.now())
                .isBlock(false)
                .chatRoom(chatRoom)
                .chatUser(receiver)
                .build();

        chatRoom.insertUserChatRooms(senderUserChatRoom);
        chatRoom.insertUserChatRooms(receiverUserChatRoom);

        //채팅방 저장
        chatRoomRepository.save(chatRoom);

        //1:1 채팅일 경우 수신자 정보 저장
        userChatRoomRepository.save(receiverUserChatRoom);

        //사용자 채팅목록 저장
        userChatRoomRepository.save(senderUserChatRoom);

        CreateDmChatRoomResponse response = CreateDmChatRoomResponse.builder()
                .chatRoomId(chatRoom.getId())
                .build();

        return response;

    }
    @Override
    @Transactional
    public void createGroupChatRoom(CreateChatRoomRequest createChatRoomRequest) {
        //송신자
        User sender =
                userRepository.findById(createChatRoomRequest.getSenderId()).orElseThrow(
                        () -> new RuntimeException("송신자를 찾지 못합니다.")
                );

        //스터디그룹 가져오기
        StudyGroup studyGroup =
                studyGroupRepository.findById(createChatRoomRequest.getGroupId()).orElseThrow(
                () -> new BusinessException(StudyGroupErrorCode.STUDY_GROUP_NOT_FOUND)
        );

        // 채팅방 새로 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .chatRoomType(ChatRoomType.GROUP)
                .createdAt(LocalDateTime.now())
                .studyGroup(studyGroup)
                .build();


        // 사용자 채팅방 생성(송신자)
        UserChatRoom senderUserChatRoom = UserChatRoom.builder()
                .joinedAt(LocalDateTime.now())
                .isBlock(false)
                .chatRoom(chatRoom)
                .chatUser(sender)
                .build();

        chatRoom.insertUserChatRooms(senderUserChatRoom);

        //채팅방 저장
        chatRoomRepository.save(chatRoom);
        //사용자 채팅목록 저장
        userChatRoomRepository.save(senderUserChatRoom);

    }


    /**
     * 사용자의 채팅방 목록 불러오기 서비스
     * 사용자의 채팅 목록을 불러온 뒤, 채팅방들의 id값을 얻어내어
     * 채팅방 정보를 가져와 response dto로 반환
     */
    @Override
    public ChatRoomListResponse getUserChatRooms(Long userId) {
        Optional<List<UserChatRoom>> optionalUserChatRooms = userChatRoomRepository.findByChatUserId(userId);
        if (optionalUserChatRooms.isPresent()) {

            List<UserChatRoom> userChatRooms = optionalUserChatRooms.get();
            List<ChatRoomDto> chatRooms = new ArrayList<>();
            //리스트에 있는 값들에서 chatRoomId로 ChatRooms 찾아내기
            for (UserChatRoom userChatRoom : userChatRooms) {
                Long chatRoomId = userChatRoom.getChatRoom().getId();
                ChatRoom chatRoom = findByChatRoomId(chatRoomId);
                chatRooms.add(fromChatRoomEntity(chatRoom, userId));
            }
            ChatRoomListResponse chatRoomListResponse = ChatRoomListResponse.builder()
                    .userId(userId)
                    .chatRooms(chatRooms)
                    .build();
            return chatRoomListResponse;
        } else {
            return ChatRoomListResponse.builder().build();
        }
    }

    /**
     * 채팅방의 채팅목록 불러오기 서비스
     */
    @Override
    public List<ChatMessage> findChatRoomMessages(Long chatRoomId) {
        Optional<List<ChatMessage>> optionalChatMessages =
                chatMessageRepository.findByChatRoomId(chatRoomId);
        List<ChatMessage> chatMessages = new ArrayList<>();
        if (optionalChatMessages.isPresent()) {
            chatMessages = optionalChatMessages.get();

        }
        return chatMessages;
    }

    /**
     * 최신메시지 가져오기 서비스
     */
    public ChatRecentMessageDto findRecentMessage(Long chatRoomId, Long userId) {
        Optional<ChatMessage> optionalChatMessage =
                chatMessageRepository.findFirstByChatRoomIdOrderByCreatedAtDesc(chatRoomId);
        if (optionalChatMessage.isPresent()) {
            ChatMessage chatMessage = optionalChatMessage.get();
        //채팅메시지에서 송신자 조회
        User chatSender = userRepository.findById(chatMessage.getChatSender().getId()).orElseThrow(
                () ->new BusinessException(UserErrorCode.USER_NOT_EXIST));

        //송신자가 누구던간에, 채팅목록을 띄우고 있는 인원이 이 메시지를 읽었는지 확인이 필요
        boolean isRead = chatReadRepository.existsByChatMessageIdAndChatUserId(chatMessage.getId(), userId);

        return ChatRecentMessageDto.builder()
                .id(chatMessage.getId())
                .nickName(chatSender.getNickname())
                .profileImgUrl(chatSender.getImage())
                .content(chatMessage.getContent())
                .isRead(isRead)
                .createdAt(chatMessage.getCreatedAt())
                .build();
        } else {
            User user = userRepository.findById(userId).orElseThrow(
                    () -> new BusinessException(UserErrorCode.USER_NOT_EXIST)
            );
            return ChatRecentMessageDto.builder()
                    .nickName(user.getNickname())
                    .profileImgUrl(user.getImage())
                    .content("아직 채팅메시지가 없습니다!")
                    .userId(userId)
                    .isRead(true)
                    .createdAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * 채팅방 entity -> dto로 변환 서비스
     */
    public ChatRoomDto fromChatRoomEntity(ChatRoom chatRoom, Long userId) {
        //1:1 채팅은 그룹아이디가 null
        Long groupId = null;
        String chatRoomName = "";
        //group일 경우
        if (chatRoom.getStudyGroup() != null) {
            groupId = chatRoom.getStudyGroup().getId();
            StudyGroup studyGroup = studyGroupRepository.findById(groupId).orElseThrow(
                    () -> new BusinessException(StudyGroupErrorCode.STUDY_GROUP_NOT_FOUND)
            );
            chatRoomName = studyGroup.getName();
        } else {//dm일 경우
            //채팅방 아이디로 모든 사용자 채팅방 검색
            List<UserChatRoom> userChatRooms =
                    userChatRoomRepository.findByChatRoomId(chatRoom.getId()).orElseThrow(
                            () -> new BusinessException(ChatErrorCode.USER_CHAT_ROOM_NOT_FOUND)
                    );
            for (UserChatRoom userChatRoom : userChatRooms) {
                if (!userChatRoom.getChatUser().getId().equals(userId)) {
                    chatRoomName = userChatRoom.getChatUser().getNickname();
                }
            }
            //둘 중에서, 받아온 userId가 아닌 entity의 사용자명
        }

        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .chatRoomType(chatRoom.getChatRoomType())
                .groupId(groupId)
                .unreadMessages(findUnreadMessageIds(chatRoom.getId(), userId))
                .recentMessage(findRecentMessage(chatRoom.getId(), userId))
                .chatRoomName(chatRoomName)
                .build();
    }

    /**
     * 1:1채팅에서 두 사용자 간의 이전 채팅 기록 count 확인 서비스
     * */
    @Override
    public ChatMessageCountCkResponse findChatMessageRecord(Long senderId, Long receiverId) {
        ChatMessageCountCkResponse response = ChatMessageCountCkResponse.builder()
                .count(
                        chatCustomRepository
                                .countByChatSenderIdAndChatReceiverIdAndRoomTypeDM(senderId, receiverId)
                )
                .build();
        return response;
    }

    @Override
    public ChatMessageCountCkResponse findChatMessageRecordForGroup(Long groupId) {
        ChatRoom chatRoom = findByGroupId(groupId);
        int count = chatMessageRepository.countByChatRoomId(chatRoom.getId());
        ChatMessageCountCkResponse response = ChatMessageCountCkResponse.builder()
                .count(Long.parseLong(String.valueOf(count)))
                .build();
        return response;
    }

    /**
     * 그룹채팅 가입 서비스
     * 사용자 아이디와 그룹아이디로
     * */
    @Override
    @Transactional
    public ChatRoomJoinResponse joinChatRoom(Long groupId) {

        UserResponse userResponse =
                (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User joinUser =
                userRepository.findById(userResponse.getId()).orElseThrow(
                        () -> new RuntimeException(String.valueOf(UserErrorCode.USER_NOT_EXIST))
                );

        ChatRoom chatRoom = findByGroupId(groupId);
        UserChatRoom userChatRoom = UserChatRoom.builder()
                .joinedAt(LocalDateTime.now())
                .isBlock(false)
                .chatRoom(chatRoom)
                .chatUser(joinUser)
                .build();
        userChatRoomRepository.save(userChatRoom);

        ChatRoomJoinResponse chatRoomJoinResponse = ChatRoomJoinResponse.builder()
                .result(true)
                .build();
        return chatRoomJoinResponse;
    }


    /**
     * 사용자가 해당 채팅방을 구독중인지 확인하는 서비스
     * */
    @Override
    public boolean isJoinedChatRoom(Long chatUserId, Long chatRoomId) {
        boolean isJoined = userChatRoomRepository.existsByChatUserIdAndChatRoomId(chatUserId, chatRoomId);
        if (!isJoined) {
            throw new BusinessException(ChatErrorCode.USER_CHAT_ROOM_NOT_FOUND);
        }
        return true;
    }

    /**
     * 채팅방 id를 받아 List<ChatRoomMemberDto>로 return 서비스
     */
    @Override
    public List<ChatRoomMemberDto> getChatRoomMembers(ChatRoom chatRoom) {

        List<ChatRoomMemberDto> chatRoomMembers = new ArrayList<>();

        //해당 채팅방에 참여하고 있다는걸 알려주는 user_chat_room가져오기
        if (chatRoom.getChatRoomType().equals(ChatRoomType.DM)) {

            List<UserChatRoom> userChatRooms =
                    userChatRoomRepository.findByChatRoomId(chatRoom.getId())
                            .orElseThrow(
                                    ()->new BusinessException(ChatErrorCode.USER_CHAT_ROOM_NOT_FOUND)
                    );
            for (UserChatRoom userChatRoom : userChatRooms) {
                ChatRoomMemberDto chatRoomMemberDto = ChatRoomMemberDto.builder()
                        .id(userChatRoom.getChatUser().getId())
                        .nickName(userChatRoom.getChatUser().getNickname())
                        .profileImg(userChatRoom.getChatUser().getImage())
                        .build();
                chatRoomMembers.add(chatRoomMemberDto);
            }
        } else {

            for (GroupMember groupMember : chatRoom.getStudyGroup().getMembers()) {
                ChatRoomMemberDto chatRoomMemberDto = ChatRoomMemberDto.builder()
                        .id(groupMember.getMember().getId())
                        .nickName(groupMember.getNickname())
                        .profileImg(groupMember.getMember().getImage())
                        .build();
                chatRoomMembers.add(chatRoomMemberDto);
            }
        }

        return chatRoomMembers;
    }
    
    /**
     * 채팅메시지 읽음 update 서비스
     * RabbitMQListener
     * */
    @Override
    @Transactional
    public void updateReadCount(ChatReadRequest chatReadRequest, Long chatRoomId) {
        // 메시지 읽음 처리 요청을 큐에 추가
        try {
            String messageJson = objectMapper.writeValueAsString(chatReadRequest);
            messagingTemplate
                    .convertAndSend("readCheck.exchange", "readCheck.request." + chatRoomId, messageJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
        } catch (AmqpException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
        }
    }

    @Override
    public List<Long> findUnreadMessageIds(Long chatRoomId, Long userId) {
        return chatCustomRepository.findUnreadMessageIds(chatRoomId, userId);
    }
    /**읽지 않은 메시지들의 읽음처리 / 읽지 않은 사람 수 update 서비스*/
//    @Override
//    @Transactional
//    public void updateAndInsertChatReadsAnother(Long userId, Long chatRoomId) {
//        List<Long> findUnreadMessageIds = findUnreadMessageIds(chatRoomId, userId);
//        //바꿀게 없으면 pass
//        if (!findUnreadMessageIds.isEmpty()) {
//            //insertUnreadMessagesToChatRead
//            insertChatReads(request, userId);
//            updateChatReads(request);
//            //해당 채팅방을 구독하고 있는 사람들에게, 메시지들이 읽음처리 되었음을 rabbitmq로 알림.
//            try {
//                //현재 채팅방의 메시지 읽음처리된 id list만 보내야함
//                String messageJson = objectMapper.writeValueAsString(request);
//                messagingTemplate
//                        .convertAndSend(READ_CHECK_EXCHANGE_NAME, READ_CHECK_BULK_RESPONSE_ROUTING_KEY + chatRoomId, messageJson);
//            } catch (JsonProcessingException e) {
//                e.printStackTrace();
//                throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
//            } catch (AmqpException e) {
//                e.printStackTrace();
//                throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
//            }
//        }
//    }

    /**읽지 않은 메시지들의 읽음처리 / 읽지 않은 사람 수 update 서비스*/
    @Override
    @Transactional
    public void updateAndInsertChatReads(ChatUpdateReadMessagesRequest request, Long userId, Long chatRoomId) {
        //바꿀게 없으면 pass
        if (!request.getUnreadMessages().isEmpty()) {
            insertChatReads(request, userId);
            updateChatReads(request);
            //해당 채팅방을 구독하고 있는 사람들에게, 메시지들이 읽음처리 되었음을 rabbitmq로 알림.
            try {
                //현재 채팅방의 메시지 읽음처리된 id list만 보내야함
                String messageJson = objectMapper.writeValueAsString(request);
                messagingTemplate
                        .convertAndSend(READ_CHECK_EXCHANGE_NAME, READ_CHECK_BULK_RESPONSE_ROUTING_KEY + chatRoomId, messageJson);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
            } catch (AmqpException e) {
                e.printStackTrace();
                throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
            }
        }
    }
    /**
     * 읽지 않은 메시지들 모두 읽음 처리(insert)
     * */
    @Override
    public void insertChatReads(ChatUpdateReadMessagesRequest request, Long userId) {
        chatCustomRepository.insertChatReads(request, userId);
    }
    /**
     * 해당 메시지들 unreadCount 전부 -1
     * */
    @Override
    public void updateChatReads(ChatUpdateReadMessagesRequest request) {
        chatCustomRepository.updateChatReads(request);
    }

    /**사용자의 한 그룹의 읽지 않은 메시지들 모두 읽음 처리*/
    @Override
    @Transactional
    public void updateGroupUnreadMessages(Long userId, Long chatRoomId, Long groupId) {
        
        //사용자가 해당 채팅방에 있는 사람인지 체크
        isJoinedChatRoom(userId, chatRoomId);
        //채팅방의 읽지 않은 메시지 id들 받아오기
        List<Long> unreadMessageIds = findUnreadMessageIds(chatRoomId, userId);
        //읽지 않은 메시지가 존재할 때 읽음처리
        System.out.println("useId : "+userId+", 채팅방 id : "+chatRoomId);
        if (!unreadMessageIds.isEmpty()) {
            ChatUpdateReadMessagesRequest request = ChatUpdateReadMessagesRequest.builder()
                    .unreadMessages(unreadMessageIds)
                    .chatRoomId(chatRoomId)
                    .build();
            //읽음 처리
            updateAndInsertChatReads(request, userId, chatRoomId);
        } else {
            System.out.println("읽지 않은 메시지 없음.");
        }    
    }

    /**사용자의 한 채팅방의 읽지 않은 메시지들 모두 읽음 처리*/
    @Override
    @Transactional
    public void updateGroupUnreadMessagesForDM(Long userId, Long chatRoomId) {
        //사용자가 해당 채팅방에 있는 사람인지 체크
        isJoinedChatRoom(userId, chatRoomId);
        //채팅방의 읽지 않은 메시지 id들 받아오기
        List<Long> unreadMessageIds = findUnreadMessageIds(chatRoomId, userId);
        //읽지 않은 메시지가 존재할 때 읽음처리
        System.out.println("useId : "+userId+", 채팅방 id : "+chatRoomId);
        if (!unreadMessageIds.isEmpty()) {
            ChatUpdateReadMessagesRequest request = ChatUpdateReadMessagesRequest.builder()
                    .unreadMessages(unreadMessageIds)
                    .chatRoomId(chatRoomId)
                    .build();
            //읽음 처리
            updateAndInsertChatReads(request, userId, chatRoomId);
        } else {
            System.out.println("읽지 않은 메시지 없음.");
        }
    }

    /**
     * /두 사용자의 아이디를 받아 1:1채팅방 아이디를 return 하는 서비스
     * 두 사용자 간의 채팅기록이 없을 때 사용함
     * */
    @Override
    public Long findChatRoomIdByUserIds(Long senderId, Long receiverId) {
        return chatCustomRepository.findChatRoomIdByUserIds(senderId, receiverId);
    }
    /**
     * /두 사용자의 아이디를 받아 1:1채팅방 아이디를 return 하는 서비스
     * 두 사용자 간의 채팅기록이 존재할 때 사용함
     * */
    @Override
    public Long findExistingChatRoomId(Long senderId, Long receiverId) {
        return chatCustomRepository.findExistingChatRoomId(senderId, receiverId);
    }

    /**사용자가 채팅방에서 읽지 않은 메시지들을 모두 읽음처리하는 서비스*/
    @Override
    public void insertUnreadMessagesToChatRead(Long chatRoomId) {

        UserResponse userResponse =
                (UserResponse) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        chatCustomRepository.insertUnreadMessagesToChatRead(chatRoomId, userResponse.getId());
    }

    /** 현재 채팅방에 접속한 유저를 접속 멤버 리스트에 추가*/
    @Override
    @Transactional
    public ChatRoomConnectedUserResponse insertChatRoomConnectedUsers(ChatRoomConnectedUserRequest request,  Long chatRoomId) {
        String CHAT_ROOM_KEY = "chatRoom_"+chatRoomId+":connectedUsers";
        //sse 상태설정
        ChatRoom chatRoom = findByChatRoomId(chatRoomId);
        if (chatRoom.getChatRoomType().equals(ChatRoomType.GROUP)) {
            Long groupId = chatRoom.getStudyGroup().getId();
            Long userId = Long.parseLong(request.getConnectedMemberId());
            studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.CHATTING);
        }

        // 리스트에 멤버 추가
        addConnectedMember(request.getConnectedMemberId(), CHAT_ROOM_KEY);
        // redis에서 접속 멤버 리스트를 ChatRoomConnectedUserResponse로 받아옴
        Set<String> connectedMembers = getConnectedMembers(CHAT_ROOM_KEY);
        // return
        return ChatRoomConnectedUserResponse.builder()
                .connectedMemberIds(connectedMembers)
                .build();
    }

    /** 현재 채팅방에 접속한 유저를 접속 멤버 리스트에서 삭제*/
    @Override
    @Transactional
    public ChatRoomConnectedUserResponse removeChatRoomConnectedUsers(ChatRoomConnectedUserRequest request, Long chatRoomId) {
        String CHAT_ROOM_KEY = "chatRoom_"+chatRoomId+":connectedUsers";
        //sse 상태설정
        ChatRoom chatRoom = findByChatRoomId(chatRoomId);
        if (chatRoom.getChatRoomType().equals(ChatRoomType.GROUP)) {
            Long groupId = chatRoom.getStudyGroup().getId();
            Long userId = Long.parseLong(request.getConnectedMemberId());
            studyGroupSseService.sendMemberStatusToGroup(groupId, userId, ConnectionStatus.ONLINE);
        }
        // 리스트에서 멤버 삭제
        removeConnectedMember(request.getConnectedMemberId(), CHAT_ROOM_KEY);
        // redis에서 접속 멤버 리스트를 ChatRoomConnectedUserResponse로 받아옴
        Set<String> connectedMembers = getConnectedMembers(CHAT_ROOM_KEY);
        // return
        return ChatRoomConnectedUserResponse.builder()
                .connectedMemberIds(connectedMembers)
                .build();
    }

    // redis에서 채팅방 접속 중인 사용자 가져오기
    public Set<String> getConnectedMembers(String CHAT_ROOM_KEY) {
        return redisTemplate.opsForSet().members(CHAT_ROOM_KEY);
    }

    // redis에서 채팅방 접속 중인 사용자 추가
    public void addConnectedMember(String connectedMemberId, String CHAT_ROOM_KEY) {
        if (connectedMemberId != null){
        redisTemplate.opsForSet().add(CHAT_ROOM_KEY, String.valueOf(connectedMemberId));
        }
    }

    // redis에서 접속 중인 사용자 삭제
    public void removeConnectedMember(String connectedMemberId, String CHAT_ROOM_KEY) {
        if (connectedMemberId != null){
            redisTemplate.opsForSet().remove(CHAT_ROOM_KEY, connectedMemberId);
        }
    }

    /**채팅목록으로 채팅방의 최신 메시지 전달 서비스*/
    @Override
    public ChatRecentMessageResponse sendRecentMessageToChatList(ChatRecentMessageRequest request) {

        //request에서 값 뽑아서 response에 저장. return
        ChatRecentMessageResponse response = ChatRecentMessageResponse.builder()
                .id(request.getId())
                .senderId(request.getSenderId())
                .chatRoomId(request.getChatRoomId())
                .nickName(request.getNickName())
                .profileImgUrl(request.getProfileImgUrl())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        return response;
    }

    @Override
    public boolean isReadCheck(ChatReadRequest request) {
        return chatReadRepository.existsByChatMessageIdAndChatUserId(request.getChatMessageId(), request.getChatUserId());
    }
    /**
     * 사용자 채팅방 삭제
     * 그룹 탈퇴 시 사용자가 해당 그룹의 채팅방에서도 탈퇴
     * */
    @Override
    @Transactional
    public void deleteUSerChatRoom(Long groupId, Long userId) {

       ChatRoom chatRoom = findByGroupId(groupId);
       UserChatRoom userChatRoom = userChatRoomRepository.findByChatRoomIdAndChatUserId(chatRoom.getId(), userId);
       userChatRoomRepository.delete(userChatRoom);
    }

    public ChatMessage findChatMessage(Long chatMessageId) {
        return chatMessageRepository.findById(chatMessageId).orElseThrow(
                () -> new BusinessException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND)
        );
    }
}

package com.mercury.star_be.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercury.star_be.chat.dto.common.ChatRabbitPayload;
import com.mercury.star_be.chat.dto.common.ChatRecentMessageDto;
import com.mercury.star_be.chat.dto.request.ChatReadRequest;
import com.mercury.star_be.chat.dto.request.ChatUpdateReadMessagesRequest;
import com.mercury.star_be.chat.dto.response.ChatMessageResponse;
import com.mercury.star_be.chat.dto.response.ChatReadResponse;
import com.mercury.star_be.chat.entity.ChatMessage;
import com.mercury.star_be.chat.entity.ChatRead;
import com.mercury.star_be.chat.repository.ChatMessageRepository;
import com.mercury.star_be.chat.repository.ChatReadRepository;
import com.mercury.star_be.global.common.ApiResponse;
import com.mercury.star_be.global.config.RabbitMQConfig;
import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.code.ChatErrorCode;
import com.mercury.star_be.global.error.code.UserErrorCode;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.mercury.star_be.global.config.RabbitMQConfig.*;

@Service
@RequiredArgsConstructor
public class ChatRabbitMQListener {

    private final ChatReadRepository chatReadRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 채팅방으로 메시지 전송
     * */
    @RabbitListener(queues = "chat.queue")
    public void sendMessage(String messageJson) {
        System.out.println(messageJson);
        try {
            ChatRabbitPayload payload = objectMapper.readValue(messageJson, ChatRabbitPayload.class);
            ChatMessageResponse response = objectMapper.convertValue(payload.getObject(), ChatMessageResponse.class);
            Long chatRoomId = payload.getChatRoomId();
            messagingTemplate.convertAndSend("/topic/chat." + chatRoomId, ApiResponse.success(response));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
        }
    }
    /**
     * 채팅방의 메시지를 채팅목록의 최신 메시지로 보냄
     * */
    @RabbitListener(queues = CHAT_RECENT_MESSAGE_QUEUE_NAME)
    public void sendRecentMessage(String messageJson) {
        ChatRecentMessageDto dto = null;
        try {
            dto = objectMapper.readValue(messageJson, ChatRecentMessageDto.class);
            //이부분 변경필요
            messagingTemplate.convertAndSend("/topic/chat.recentMessage." + dto.getUserId(), messageJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
        } catch (MessagingException e) {
                e.printStackTrace();
            throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
        }
    }

    /**
     * 채팅방에 메시지를 보냄
     * 해당 채팅방을 구독하고 있는 사람들이 메시지를 받음
     * */


    @RabbitListener(queues = READ_CHECK_BULK_RESPONSE_QUEUE_NAME)
    public void sendUpdatedMessageIds(String messageJson){
        ChatUpdateReadMessagesRequest request = null;
        try {
            request = objectMapper.readValue(messageJson, ChatUpdateReadMessagesRequest.class);
            messagingTemplate.convertAndSend("/topic/readCheck.bulkResponse." + request.getChatRoomId(), messageJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
        }
    }
    /**채팅메시지 읽음 update 메시지 전달*/
    @RabbitListener(queues = "readCheck.request.queue")
    @Transactional
    public void processReadCheckMessage(String messageJson) {
        ChatReadRequest chatReadRequest = null;
        try {
            chatReadRequest = objectMapper.readValue(messageJson, ChatReadRequest.class);
            ChatMessage chatMessage = chatMessageRepository.findById(chatReadRequest.getChatMessageId()).orElseThrow(
                    () -> new BusinessException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND)
            );
            //특정 유저가 채팅메시지를 읽었는지 확인
            if (!chatReadRepository.existsByChatMessageIdAndChatUserId(chatReadRequest.getChatMessageId(), chatReadRequest.getChatUserId())) {
                if (chatMessage.getUnreadCount() > 0) {
                    chatMessage.updateUnreadCount(chatMessage.getUnreadCount() - 1);
                }

                User chatUser = userRepository.findById(chatReadRequest.getChatUserId()).orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));
                ChatRead chatRead = ChatRead.builder()
                        .chatUser(chatUser)
                        .chatMessage(chatMessage)
                        .createdAt(LocalDateTime.now())
                        .build();

                chatReadRepository.save(chatRead);

                ChatReadResponse chatReadResponse = ChatReadResponse.builder()
                        .chatMessageId(chatMessage.getId())
                        .unreadCount(chatMessage.getUnreadCount())
                        .build();

                try {
                    String readMessageJson = objectMapper.writeValueAsString(chatReadResponse);
                    messagingTemplate.convertAndSend("/topic/readCheck.response." + chatMessage.getChatRoom().getId(), readMessageJson);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
                } catch (MessagingException e) {
                    e.printStackTrace();
                    throw new BusinessException(ChatErrorCode.MESSAGE_SENDING_ERROR);
                }
            } else {
                throw new BusinessException(ChatErrorCode.CHAT_ALREADY_READ);
            }
        } catch (BusinessException e) {
            // 예외 처리: 이미 읽음 표시된 메시지에 대해 로깅
            if (chatReadRequest != null) {
                System.err.println("이미 읽음 표시된 메시지입니다. 메시지 ID: " + chatReadRequest.getChatMessageId() + ", 사용자 ID: " + chatReadRequest.getChatUserId());
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new BusinessException(ChatErrorCode.CHAT_MESSAGE_CONVERT_ERROR);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

}

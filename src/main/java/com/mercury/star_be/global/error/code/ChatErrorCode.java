package com.mercury.star_be.global.error.code;

import com.google.api.Http;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅메시지를 찾을 수 없습니다."),
    USER_CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 채팅방을 찾을 수 없습니다."),
    CHAT_MESSAGE_CONVERT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "메시지 변환이 올바르게 적용되지 않았습니다."),
    MESSAGE_SENDING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "RabbitMQ로 메시지를 전달하지 못했습니다."),
    CHAT_UNREADCOUNT_NOT_UPDATED(HttpStatus.INTERNAL_SERVER_ERROR, "메시지 읽음 상태가 UPDATE되지 못했습니다."),
    CHAT_ALREADY_READ(HttpStatus.BAD_REQUEST, "이미 읽음표시 되었습니다."),
    CHAT_ROOM_CONNECTED_MEMBER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "접속중인 사용자 정보를 가져올 수 없습니다");
    private final HttpStatus httpStatus;
    private final String message;
}

package com.mercury.star_be.chat.dto.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatEvent {
    SEND_TEXT_MESSAGE,
    SEND_FILE_MESSAGE,
    SEND_RECENT_MESSAGE_TO_CHAT_LIST,
    READ_CHECK,
    CONNECT,
    DISCONNECT;
}

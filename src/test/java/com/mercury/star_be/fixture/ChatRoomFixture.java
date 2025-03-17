package com.mercury.star_be.fixture;

import com.mercury.star_be.chat.entity.ChatRoom;
import com.mercury.star_be.chat.entity.ChatRoomType;
import com.mercury.star_be.studygroup.entity.StudyGroup;

public class ChatRoomFixture {

	public static ChatRoom createChatRoom(ChatRoomType chatRoomType, StudyGroup studyGroup) {
		return ChatRoom.builder()
			.chatRoomType(chatRoomType)
			.studyGroup(studyGroup)
			.build();
	}
}

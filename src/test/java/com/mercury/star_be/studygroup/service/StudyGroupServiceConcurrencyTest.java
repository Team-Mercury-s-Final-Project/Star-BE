package com.mercury.star_be.studygroup.service;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.mercury.star_be.chat.entity.ChatRoom;
import com.mercury.star_be.chat.entity.ChatRoomType;
import com.mercury.star_be.chat.repository.ChatRoomRepository;
import com.mercury.star_be.fixture.ChatRoomFixture;
import com.mercury.star_be.fixture.StudyGroupFixture;
import com.mercury.star_be.fixture.UserFixture;
import com.mercury.star_be.studygroup.entity.StudyGroup;
import com.mercury.star_be.studygroup.repository.StudyGroupRepository;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class StudyGroupServiceConcurrencyTest {

	@Autowired
	private StudyGroupService studyGroupService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StudyGroupRepository studyGroupRepository;

	@Autowired
	private ChatRoomRepository chatRoomRepository;

	void createUserAndGroup() {
		for (int i = 1; i <= 100; i++) {
			User user = UserFixture.createUser("user" + i);
			userRepository.save(user);
		}

		StudyGroup studyGroup = StudyGroupFixture.createStudyGroup("group", 20);
		studyGroupRepository.save(studyGroup);

		ChatRoom chatRoom = ChatRoomFixture.createChatRoom(ChatRoomType.GROUP, studyGroup);
		chatRoomRepository.save(chatRoom);
	}

	@Test
	@DisplayName("최대 인원이 20명인 그룹에 100명의 사용자가 동시에 가입해도 최대 인원 까지만 가입된다.")
	void joinStudyGroupConcurrencyTest() throws InterruptedException {
		// given
		createUserAndGroup();

		// when
		int numberOfRequests = 100;
		ExecutorService executorService = Executors.newFixedThreadPool(20);
		CountDownLatch latch = new CountDownLatch(numberOfRequests);

		for (int i = 1; i <= numberOfRequests; i++) {
			long userId = i;
			executorService.submit(() -> {
				try {
					studyGroupService.joinStudyGroup(1L, userId, null);
					System.out.println(">>> 사용자 " + userId + " 가입 완료");
				} catch (Exception e) {
					System.err.println(">>> 사용자 " + userId + " 오류 발생: " + e.getMessage());
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();
		executorService.shutdown();

		// then
		StudyGroup studyGroup = studyGroupRepository.findById(1L).orElseThrow();
		assertThat(studyGroup.getMemberCount()).isEqualTo(studyGroup.getMaxCapacity());
	}
}

package com.mercury.star_be.fixture;

import com.mercury.star_be.user.entity.User;

public class UserFixture {

	public static User createUser(String nickname) {
		return User.builder()
			.nickname(nickname)
			.build();
	}
}

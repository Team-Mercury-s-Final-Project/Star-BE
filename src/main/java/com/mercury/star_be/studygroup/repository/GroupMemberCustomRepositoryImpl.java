package com.mercury.star_be.studygroup.repository;

import static com.mercury.star_be.studygroup.entity.QGroupMember.*;
import static com.mercury.star_be.timer.entity.QTimer.*;
import static com.mercury.star_be.user.entity.QUser.*;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.mercury.star_be.studygroup.dto.GroupMemberDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class GroupMemberCustomRepositoryImpl implements GroupMemberCustomRepository {

	private final JPAQueryFactory jpaQueryFactory;

	@Override
	public List<GroupMemberDto> findByGroupId(Long groupId) {
		return jpaQueryFactory.select(
			Projections.constructor(GroupMemberDto.class,
				groupMember.member.id,
				groupMember.nickname,
				user.image,
				groupMember.isHost,
				timer.totalTime,
				groupMember.group.id
			)
		)
			.from(groupMember)
			.join(user).on(groupMember.member.id.eq(user.id))
			.leftJoin(timer).on(groupMember.id.eq(timer.groupMember.id)
				.and(timer.studyDate.eq(LocalDate.now()))
			)
			.where(groupMember.group.id.eq(groupId))
			.orderBy(groupMember.nickname.asc())
			.fetch();
	}
}

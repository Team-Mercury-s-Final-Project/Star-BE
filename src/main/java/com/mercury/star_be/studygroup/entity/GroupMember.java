package com.mercury.star_be.studygroup.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.mercury.star_be.timer.entity.Timer;
import com.mercury.star_be.user.entity.User;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
public class GroupMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(length = 20)
	private String nickname;
	private boolean isHost;
	private LocalDateTime joinedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id")
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private User member;
	@OneToMany(mappedBy = "groupMember",cascade = CascadeType.REMOVE)
	private List<Timer> timers;

	@Builder
	public GroupMember( String nickname,  boolean isHost, StudyGroup group ,User member, LocalDateTime joinedAt) {
		this.nickname = nickname;
		this.isHost = isHost;
		this.group = group;
		this.member = member;
		this.joinedAt = joinedAt;
	}

	public void setHost() {
		this.isHost = true;
	}

	public void setGuest() {
		this.isHost = false;
	}

	public String changeNickname(String nickname) {
		this.nickname = nickname;
		return this.nickname;
	}
}

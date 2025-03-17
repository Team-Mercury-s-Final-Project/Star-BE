package com.mercury.star_be.studygroup.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.mercury.star_be.chat.entity.ChatRoom;
import com.mercury.star_be.timer.entity.Timer;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
public class StudyGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(length = 50)
	private String name;
	@Column(length = 255)
	private String description;
	private String image;
	private int maxCapacity;
	private int memberCount;
	private boolean isPublic;
	private boolean hasPassword;
	@Column(length = 50)
	private String password;
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<GroupMember> members = new ArrayList<>();

	@OneToMany(mappedBy = "studyGroup", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Notice> notices = new ArrayList<>();

	@OneToMany(mappedBy = "studyGroup", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ChatRoom> chatRooms = new ArrayList<>();

	@Builder
	public StudyGroup(String name, String description, String image, int maxCapacity, int memberCount, boolean isPublic,
		boolean hasPassword, String password, LocalDateTime createdAt) {
		this.name = name;
		this.description = description;
		this.image = image;
		this.maxCapacity = maxCapacity;
		this.memberCount = memberCount;
		this.isPublic = isPublic;
		this.hasPassword = hasPassword;
		this.password = password;
		this.createdAt = createdAt;
	}

	public void updateStudyGroup(String name, String description, String image, int maxCapacity, boolean isPublic,
		boolean hasPassword, String password) {
		this.name = name;
		this.description = description;
		this.image = image;
		this.maxCapacity = maxCapacity;
		this.isPublic = isPublic;
		this.hasPassword = hasPassword;
		this.password = password;
	}

	public boolean hasPassword() {
		return hasPassword;
	}

	public void addMember(GroupMember member) {
		members.add(member);
	}

	public void addNotice (Notice notice) {
		notices.add(notice);
	}

	public void decrementMemberCount() {
		this.memberCount = Math.max(this.memberCount - 1, 0);
	}

	public boolean isPasswordCorrect(String password) {
		if (hasPassword) {
			return this.password.equals(password);
		}
		return true;
	}
}

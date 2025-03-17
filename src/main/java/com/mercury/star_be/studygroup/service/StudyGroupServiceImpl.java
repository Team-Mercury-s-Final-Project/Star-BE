package com.mercury.star_be.studygroup.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mercury.star_be.chat.dto.request.CreateChatRoomRequest;
import com.mercury.star_be.chat.service.ChatService;
import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.code.StudyGroupErrorCode;
import com.mercury.star_be.global.error.code.UserErrorCode;
import com.mercury.star_be.studygroup.dto.request.ChangeGroupNicknameRequest;
import com.mercury.star_be.studygroup.dto.request.StudyGroupCreateRequest;
import com.mercury.star_be.studygroup.dto.request.StudyGroupUpdateRequest;
import com.mercury.star_be.studygroup.dto.response.ChangeGroupNicknameResponse;
import com.mercury.star_be.studygroup.dto.response.MyStudyGroupListResponse;
import com.mercury.star_be.studygroup.dto.response.PaginationResponse;
import com.mercury.star_be.studygroup.dto.response.StudyGroupCreateResponse;
import com.mercury.star_be.studygroup.dto.response.StudyGroupDetailResponse;
import com.mercury.star_be.studygroup.dto.response.StudyGroupEnterResponse;
import com.mercury.star_be.studygroup.dto.response.StudyGroupListResponse;
import com.mercury.star_be.studygroup.dto.response.StudyGroupUpdateResponse;
import com.mercury.star_be.studygroup.entity.GroupMember;
import com.mercury.star_be.studygroup.entity.StudyGroup;
import com.mercury.star_be.studygroup.repository.GroupMemberRepository;
import com.mercury.star_be.studygroup.repository.StudyGroupRepository;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.UserRepository;
import com.mercury.star_be.user.util.JwtUtil;

import lombok.RequiredArgsConstructor;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class StudyGroupServiceImpl implements StudyGroupService {

	private final StudyGroupRepository studyGroupRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final UserRepository userRepository;
	private final ChatService chatService;
	private final JwtUtil jwtUtil;
	private final StudyGroupSseService studyGroupSseService;

	@Override
	@Transactional
	public StudyGroupCreateResponse createStudyGroup(StudyGroupCreateRequest studyGroupCreateRequest, Long userId) {
		StudyGroup studyGroup = StudyGroup.builder()
			.name(studyGroupCreateRequest.getName())
			.description(studyGroupCreateRequest.getDescription())
			.image(studyGroupCreateRequest.getImage())
			.maxCapacity(studyGroupCreateRequest.getMaxCapacity())
			.memberCount(1)
			.hasPassword(studyGroupCreateRequest.hasPassword())
			.password(studyGroupCreateRequest.getPassword())
			.isPublic(studyGroupCreateRequest.getIsPublic())
			.createdAt(LocalDateTime.now())
			.build();

		User user = userRepository.findById(userId).orElseThrow();
		GroupMember groupMember = GroupMember.builder()
			.nickname(user.getNickname())
			.isHost(true)
			.group(studyGroup)
			.member(user)
			.joinedAt(LocalDateTime.now())
			.build();
		groupMemberRepository.save(groupMember);
		StudyGroup savedGroup = studyGroupRepository.save(studyGroup);

		// 그룹 생성 시 그룹 채팅방 생성
		CreateChatRoomRequest createChatRoomRequest = CreateChatRoomRequest.builder()
			.senderId(userId)
			.groupId(savedGroup.getId())
			.build();
		chatService.createGroupChatRoom(createChatRoomRequest);

		return new StudyGroupCreateResponse(savedGroup.getId());
	}


	public List<Long> getGroupIdsByMemberId(Long memberId) {
		return groupMemberRepository.findGroupIdsByMemberId(memberId);
	}


	@Override
	@Transactional
	public StudyGroupUpdateResponse updateStudyGroup(StudyGroupUpdateRequest studyGroupUpdateRequest, Long groupId,
		Long userId) {
		StudyGroup studyGroup = findById(groupId);

		// 그룹장 검증
		GroupMember groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, userId)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));
		if (!groupMember.isHost()) {
			throw new BusinessException(StudyGroupErrorCode.USER_NOT_HOST);
		}

		// 수정된 인원이 현재 인원보다 큰 지 검증
		int updateMaxCapacity = studyGroupUpdateRequest.getMaxCapacity();
		if (studyGroup.getMemberCount() > updateMaxCapacity) {
			throw new BusinessException(StudyGroupErrorCode.INVALID_MAX_CAPACITY);
		}

		// 그룹 수정
		studyGroup.updateStudyGroup(studyGroupUpdateRequest.getName(),
			studyGroupUpdateRequest.getDescription(),
			studyGroupUpdateRequest.getImage(),
			studyGroupUpdateRequest.getMaxCapacity(),
			studyGroupUpdateRequest.isPublic(),
			studyGroupUpdateRequest.hasPassword(),
			studyGroupUpdateRequest.getPassword());

		return StudyGroupUpdateResponse.builder()
			.id(studyGroup.getId())
			.name(studyGroup.getName())
			.description(studyGroup.getDescription())
			.image(studyGroup.getImage())
			.maxCapacity(studyGroup.getMaxCapacity())
			.memberCount(studyGroup.getMemberCount())
			.isPublic(studyGroup.isPublic())
			.hasPassword(studyGroup.hasPassword())
			.password(studyGroup.getPassword())
			.build();
	}

	@Override
	public StudyGroupDetailResponse getStudyGroup(Long groupId) {
		StudyGroup studyGroup = findById(groupId);
		return StudyGroupDetailResponse.builder()
			.id(studyGroup.getId())
			.name(studyGroup.getName())
			.description(studyGroup.getDescription())
			.image(studyGroup.getImage())
			.maxCapacity(studyGroup.getMaxCapacity())
			.memberCount(studyGroup.getMemberCount())
			.isPublic(studyGroup.isPublic())
			.hasPassword(studyGroup.hasPassword())
			.createdAt(studyGroup.getCreatedAt().toLocalDate())
			.build();
	}

	@Override
	public StudyGroupEnterResponse enterStudyGroup(Long groupId, Long userId) {
		StudyGroup studyGroup = studyGroupRepository.findById(groupId)
			.orElseThrow(() -> new BusinessException((StudyGroupErrorCode.STUDY_GROUP_NOT_FOUND)));
		GroupMember groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, userId)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));
		// groupMember의 isHost 값을 별도로 저장
		boolean isHost = groupMember.isHost();

		return StudyGroupEnterResponse.builder()
			.id(studyGroup.getId())
			.name(studyGroup.getName())
			.description(studyGroup.getDescription())
			.image(studyGroup.getImage())
			.maxCapacity(studyGroup.getMaxCapacity())
			.memberCount(studyGroup.getMemberCount())
			.isPublic(studyGroup.isPublic())
			.hasPassword(studyGroup.hasPassword())
			.createdAt(studyGroup.getCreatedAt().toLocalDate())
			.isHost(isHost)
			.build();
	}

	@Override
	public PaginationResponse<StudyGroupListResponse> getStudyGroupList(String keyword, String sort, String direction,
		int page) {
		int size = 30;
		Pageable pageable = PageRequest.of(page, size);

		Page<StudyGroup> studyGroups = studyGroupRepository.findAllPublicByCreationDate(keyword, sort, direction,
			pageable);

		List<StudyGroupListResponse> content = studyGroups.getContent().stream()
			.map(studyGroup -> StudyGroupListResponse.builder()
				.id(studyGroup.getId())
				.name(studyGroup.getName())
				.description(studyGroup.getDescription())
				.image(studyGroup.getImage())
				.maxCapacity(studyGroup.getMaxCapacity())
				.memberCount(studyGroup.getMemberCount())
				.isPublic(studyGroup.isPublic())
				.hasPassword(studyGroup.hasPassword())
				.createdAt(studyGroup.getCreatedAt())
				.build()
			)
			.toList();

		return new PaginationResponse<>(
			content,
			studyGroups.getNumber(),
			studyGroups.isLast()
		);
	}

	@Override
	@Transactional
	public void joinStudyGroup(Long groupId, Long userId, String password) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));

		StudyGroup studyGroup = findById(groupId);
		// 그룹 가입 여부, 인원 수, 비밀번호 검증
		validateStudyGroupJoin(studyGroup, user.getId(), password);

		// 그룹에 유저 추가
		GroupMember groupMember = GroupMember.builder()
			.group(studyGroup)
			.member(user)
			.isHost(false)
			.nickname(user.getNickname())
			.joinedAt(LocalDateTime.now())
			.build();
		int updatedRows = studyGroupRepository.increaseMemberCount(groupId);
		if (updatedRows == 0) {
			throw new BusinessException(StudyGroupErrorCode.STUDY_GROUP_IS_FULL);
		}
		studyGroup.addMember(groupMember);

		// 그룹채팅방 가입
		chatService.joinChatRoom(groupId, userId);

		// SSE: 전체 그룹원 정보 send
		studyGroupSseService.sendGroupMemberInfoToGroup(groupId);
	}

	private void validateStudyGroupJoin(StudyGroup studyGroup, Long userId, String password) {
		// 가입하려는 그룹에 이미 유저가 가입한 상태일때
		boolean isAlreadyJoined = groupMemberRepository.existsByGroupIdAndMemberId(studyGroup.getId(), userId);
		if (isAlreadyJoined) {
			throw new BusinessException(StudyGroupErrorCode.USER_ALREADY_EXIST_IN_GROUP);
		}

		// 그룹이 비밀번호로 보호되어 있다면 전달된 password와 일치하는지 검증
		if (!studyGroup.isPasswordCorrect(password)) {
			throw new BusinessException(StudyGroupErrorCode.INVALID_GROUP_PASSWORD);
		}
	}

	@Override
	@Transactional
	public void exitStudyGroup(Long groupId, Long userId) {
		StudyGroup studyGroup = findById(groupId);
		// 탈퇴하려는 사람이 그룹에 존재하는지
		GroupMember groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, userId)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));

		// 그룹의 멤버가 1명만 남아 있는 경우 (호스트 == 마지막 유저)
		if (studyGroup.getMemberCount() == 1) {
			// 그룹 삭제
			groupMemberRepository.deleteByGroupIdAndMemberId(groupId, userId);
			studyGroupRepository.delete(studyGroup);
			return; // 여기서 종료
		}

		// 유저가 호스트인 경우 새 호스트 지정
		if (groupMember.isHost()) {
			GroupMember newHost = groupMemberRepository.findFirstByGroupIdAndIdNotOrderByJoinedAtAsc
					(studyGroup.getId(), groupMember.getId())
				.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.STUDY_GROUP_IS_EMPTY));
			newHost.setHost();
			groupMemberRepository.save(newHost);
		}
		// 그룹 멤버 관계 삭제
		groupMemberRepository.deleteByGroupIdAndMemberId(studyGroup.getId(), userId);
		// 그룹의 멤버 카운트 감소
		studyGroup.decrementMemberCount();

		// 사용자 채팅방 삭제
		chatService.deleteUSerChatRoom(groupId, userId);

		// SSE: 전체 그룹원 정보 send
		studyGroupSseService.sendGroupMemberInfoToGroup(groupId);
	}

	@Override
	public void simpleExitStudyGroup(String token) {
		Long userId = jwtUtil.getId(token);
		List<Long> groupIdList = groupMemberRepository.findGroupIdsByMemberId(userId);
		for (Long groupId : groupIdList) {
			StudyGroup studyGroup = findById(groupId);
			// 그룹의 멤버가 1명만 남아 있는 경우 (호스트 == 마지막 유저)
			if (studyGroup.getMemberCount() == 1) {
				studyGroupRepository.delete(studyGroup);
				return;
			}

			// 그룹 멤버 관계 삭제
			groupMemberRepository.deleteByGroupIdAndMemberId(groupId, userId);
			studyGroup.decrementMemberCount();

			// 사용자 채팅방 삭제
			chatService.deleteUSerChatRoom(groupId, userId);

			// SSE: 전체 그룹원 정보 send
			studyGroupSseService.sendGroupMemberInfoToGroup(groupId);
		}
	}

	@Override
	@Transactional
	public void selectHost(Long groupId, Long MemberId) {

		GroupMember newHost = groupMemberRepository.findByGroupIdAndMemberId(groupId, MemberId)
						.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.STUDY_GROUP_IS_EMPTY));
		newHost.setHost();
	}

	@Override
	@Transactional
	public void changeHost(Long groupId, Long userId, Long newHostId) {

		GroupMember currentHost = groupMemberRepository.findByGroupIdAndMemberId(groupId, userId)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));
		GroupMember newHost = groupMemberRepository.findByGroupIdAndMemberId(groupId, newHostId)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));

		if (!currentHost.isHost()) {
			throw new BusinessException(StudyGroupErrorCode.USER_NOT_HOST);
		}
		// 호스트 권한 박탈
		currentHost.setGuest();
		// 새 호스트 지정
		newHost.setHost();
	}

	@Override
	@Transactional
	public ChangeGroupNicknameResponse changeGroupNickname(Long userId, Long groupId,
		ChangeGroupNicknameRequest changeGroupNicknameRequest) {
		findById(groupId);
		GroupMember groupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, userId)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.USER_NOT_EXIST_IN_GROUP));
		String changeNickname = groupMember.changeNickname(changeGroupNicknameRequest.getNickname());

		return new ChangeGroupNicknameResponse(changeNickname);
	}

	public StudyGroup findById(Long id) {
		return studyGroupRepository.findById(id)
			.orElseThrow(() -> new BusinessException(StudyGroupErrorCode.STUDY_GROUP_NOT_FOUND));
	}

	@Override
	public List<MyStudyGroupListResponse> getMyStudyGroupList(Long userId) {
		List<MyStudyGroupListResponse> myStudyGroupListResponses = studyGroupRepository.findMyStudyGroupList(userId);
		return myStudyGroupListResponses;
	}

}

package com.mercury.star_be.studygroup.service;

import java.util.List;

import com.mercury.star_be.global.error.BusinessException;
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

public interface StudyGroupService {

	StudyGroupCreateResponse createStudyGroup(StudyGroupCreateRequest studyGroupCreateRequest, Long userId);

	StudyGroupUpdateResponse updateStudyGroup(StudyGroupUpdateRequest studyGroupUpdateRequest, Long groupId,
		Long userId);

	StudyGroupDetailResponse getStudyGroup(Long groupId);

	StudyGroupEnterResponse enterStudyGroup(Long groupId, Long userId);

	PaginationResponse<StudyGroupListResponse> getStudyGroupList(String keyword, String sort, String direction,
		int page);

	void joinStudyGroup(Long groupId, Long userId, String password);

	void simpleExitStudyGroup(String token);

	void selectHost(Long groupId, Long MemberId);

	void exitStudyGroup(Long groupId, Long userId);

	void changeHost(Long groupId, Long userId, Long newHostId);

	ChangeGroupNicknameResponse changeGroupNickname(Long userId, Long groupId,
		ChangeGroupNicknameRequest changeGroupNicknameRequest);

	List<MyStudyGroupListResponse> getMyStudyGroupList(Long userId);
}

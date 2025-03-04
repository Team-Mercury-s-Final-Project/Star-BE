package com.mercury.star_be.user.service;


import com.mercury.star_be.file.service.FileService;
import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.CustomAuthenticationException;
import com.mercury.star_be.global.error.code.AuthenticationErrorCode;
import com.mercury.star_be.global.error.code.UserErrorCode;
import com.mercury.star_be.studygroup.dto.request.GroupLeaveRequest;
import com.mercury.star_be.studygroup.dto.response.GroupMembeResponse;
import com.mercury.star_be.studygroup.dto.response.MyStudyGroupListResponse;
import com.mercury.star_be.studygroup.repository.GroupMemberRepository;
import com.mercury.star_be.studygroup.service.StudyGroupService;
import com.mercury.star_be.user.Handler.CustomSuccessHandler;
import com.mercury.star_be.user.dto.request.UserRequest;
import com.mercury.star_be.user.dto.response.*;
import com.mercury.star_be.user.entity.RefreshToken;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.RefreshRepository;
import com.mercury.star_be.user.repository.UserRepository;
import com.mercury.star_be.user.util.CookieUtil;
import com.mercury.star_be.user.util.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.mercury.star_be.global.error.code.AuthenticationErrorCode.USER_DEACTIVATED;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends DefaultOAuth2UserService implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final FileService gcsFileService;
    private final StudyGroupService studyGroupService;
    private final RefreshRepository refreshRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final EntityManager entityManager;
    private final CustomSuccessHandler customSuccessHandler;

    @Override
    public UserResponse createUser(UserRequest userRequest) {
        User user = User.builder()
                .email(userRequest.getEmail())
                .nickname(userRequest.getNickname())
                .provider(userRequest.getProvider())
                .image(userRequest.getImage())

                .build();

        User savedUser = userRepository.save(user);
        return new UserResponse(savedUser);
    }

    /**
     * oauth 로그인
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        System.out.println("CustomOAuth2UserService: " + oAuth2User);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Oauth2Response oauth2Response = null;

        if (registrationId.equals("naver")) {
            oauth2Response = new NaverResponse(oAuth2User.getAttributes());
        } else if (registrationId.equals("google")) {
            oauth2Response = new GoogleResponse(oAuth2User.getAttributes());
        } else if (registrationId.equals("kakao")) {
            oauth2Response = new kakaoResponse(oAuth2User.getAttributes());
        } else {
            return null;
        }
        String oauthId = oauth2Response.getProvider() + "_" + oauth2Response.getProviderId();

        // DB save
        UserResponse userResponseDto = saveUser(oauth2Response, oauthId);
        return (OAuth2User) userResponseDto;
    }

    /**
     * 이미 존재하는 경우 update,
     * 존재하지 않는 경우 save
     */
    @Override
    public UserResponse saveUser(Oauth2Response oauth2Response, String oauthId) {
        // DB 조회
        User existData = userRepository.findByoauthId(oauthId)
                .orElseGet(() -> {
                    User user = User.builder()
                            .email(oauth2Response.getEmail())
                            .nickname(oauth2Response.getName())
                            .provider(oauth2Response.getProvider())
                            .image(oauth2Response.getImage())
                            .oauthId(oauthId)
                            .build();
                    User savedUser = userRepository.save(user);
                    return new UserResponse(savedUser);
                });
        if (!existData.isActive()) {
            throw new CustomAuthenticationException(USER_DEACTIVATED);  // throw new AuthenticationException(USER_DEACTIVATED.getMessage()
        }

        return new UserResponse(existData);
    }


    @Override
    public Map<String, Object> getUserInfo(Authentication auth) {
        // 인증된 사용자 정보 가져오기 (JWT로부터 사용자 ID만 가져오고, DB에서 최신 정보 조회)
        UserResponse userResponse = jwtUtil.getAuthenticatedUser(auth);

        // DB에서 최신 유저 정보 조회
        User user = userRepository.findById(userResponse.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 유저 정보 Map에 담기
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("email", user.getEmail());
        userInfo.put("provider", user.getProvider());
        userInfo.put("created_at", user.getCreatedAt());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("profileImgUrl", user.getImage());
        return userInfo;
    }

    @Override
    public List<Map<String, Object>> getUserJoinedInfo(Long userId) {
        List<Map<String, Object>> groupList = new ArrayList<>();
        List<MyStudyGroupListResponse> myStudyGroupList = studyGroupService.getMyStudyGroupList(userId);

        for (MyStudyGroupListResponse myStudyGroup : myStudyGroupList) {
            Map<String, Object> groupInfoMap = new HashMap<>();

            // DTO로 매핑 로직 수정
            List<GroupMembeResponse> groupMemberDTOs = groupMemberRepository.findByGroupIdOrderByJoinedAtAsc(myStudyGroup.getId())
                    .stream()
                    .map(GroupMembeResponse::new)  // 생성자 참조로 수정
                    .collect(Collectors.toList());

            if (groupMemberDTOs.size() > 1) {  // size가 1이 아닌 경우에 대한 조건 수정
                // 내가 방장 O  & 사람들 있음
                if (groupMemberDTOs.stream().anyMatch(user -> user.isHost() && Objects.equals(user.getMemberId(), userId))) {
                    // if (Objects.equals(groupMemberDTOs.get(0).getMemberId(), userId)) {
                    groupInfoMap.put("nickname", groupMemberDTOs.get(0).getNickname());
                    groupInfoMap.put("groupId", myStudyGroup.getId());
                    groupInfoMap.put("name", myStudyGroup.getName());
                    groupInfoMap.put("imageUrl", myStudyGroup.getImageUrl());
                    groupInfoMap.put("members", groupMemberDTOs);
                    groupInfoMap.put("selectedMembers", groupMemberDTOs.get(1));  // 두 번째 멤버
                    groupInfoMap.put("isHost", "1");
                } else {
                    // 내가 방장 X  & 사람들 있음

                    String nickname = null;
                    for (GroupMembeResponse groupMemberDTO : groupMemberDTOs) {
                        if (Objects.equals(groupMemberDTO.getMemberId(), userId)) {
                            nickname = groupMemberDTO.getNickname();
                            break;  // 첫 번째로 일치하는 값 찾으면 루프 종료
                        }
                    }
                    groupInfoMap.put("nickname", nickname);
                    groupInfoMap.put("groupId", myStudyGroup.getId());
                    groupInfoMap.put("name", myStudyGroup.getName());
                    groupInfoMap.put("imageUrl", myStudyGroup.getImageUrl());
                    groupInfoMap.put("members", groupMemberDTOs);
                    groupInfoMap.put("isHost", "0");
                }
            } else { // 나 혼자 있는 방
                groupInfoMap.put("nickname", groupMemberDTOs.get(0).getNickname());
                groupInfoMap.put("groupId", myStudyGroup.getId());
                groupInfoMap.put("name", myStudyGroup.getName());
                groupInfoMap.put("imageUrl", myStudyGroup.getImageUrl());
                groupInfoMap.put("isHost", "1");
            }
            groupList.add(groupInfoMap);
        }
        return groupList;
    }

    /**
     * 유저 정보 변경
     */
    @Override
    @Transactional
    public void updateUserInfo(Authentication auth, String nickname, MultipartFile profileImg) throws IOException {

        UserResponse authenticatedUser = jwtUtil.getAuthenticatedUser(auth);
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new CustomAuthenticationException(AuthenticationErrorCode.USER_NOTFIND) {
                });
        // 2. 닉네임 업데이트
        user.setNickname(nickname);
        // 3. 프로필 이미지가 있는 경우 파일 저장 후 URL 저장
        if (profileImg != null && !profileImg.isEmpty()) {
            String imageUrl = gcsFileService.uploadImage(profileImg).getUrl();
            user.setImage(imageUrl);
        }

        // DB에 업데이트된 유저 저장
        userRepository.save(user);
    }

    /**
     * 유저 정보 삭제 (실제로는 active = 0 수정)
     */
    @Override
    @Transactional
    public void deleteUserInfo(Long userId) {
        User deleteUser = userRepository.findById(userId)
                .orElseThrow(() -> new CustomAuthenticationException(AuthenticationErrorCode.USER_NOTFIND));
        deleteUser.setActive(false);
        userRepository.save(deleteUser);
    }


    /**
     * 유저 그룹 탈퇴
     */
    @Override
    @Transactional
    public void exituserJoinGroup(GroupLeaveRequest request, HttpServletRequest httpServletReq, Authentication auth) {

        // 그룹장 위임
        User user = JwtUtil.getAuthenticatedUser(auth);
        List<GroupLeaveRequest.GroupMemberInfo> GroupLeaveRequestList = request.getGroupMemberInfos();
        if (!GroupLeaveRequestList.isEmpty()) {
            for (GroupLeaveRequest.GroupMemberInfo GroupAndMemberInfo : GroupLeaveRequestList) {
                studyGroupService.selectHost(GroupAndMemberInfo.getGroupId(), GroupAndMemberInfo.getMemberId());
            }
        }
        // 그룹 유저 삭제 및 그룹 삭제
        studyGroupService.simpleExitStudyGroup(jwtUtil.getJwt(httpServletReq));
    }


    /**
     * 유저 토큰 재 발행
     */
    @Override
    public boolean reissue(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws ServletException, IOException {
        String accessToken = jwtUtil.getJwt(req);
        Long userId = jwtUtil.getId(accessToken);
        RefreshToken refreshToken = refreshRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomAuthenticationException(AuthenticationErrorCode.MISSING_REFRESGTOKEN));
        boolean test = (refreshToken.getExpiredAt()).after(new Date());
        if (test) {
            jwtUtil.createAuthentication(accessToken);
            User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            Long id = user.getId();
            // User 객체가 영속성 컨텍스트에 있으면 해당 객체를 가져옵니다.
            User persistentUser = entityManager.find(User.class, user.getId());
            // 토큰 생성
            String reissueAccessToken = jwtUtil.createJwt("access", id, jwtUtil.ACCESS_TOKEN_EXPIRATION);    // 24시간
            // String refreshToken = jwtUtil.createJwt("refresh", id, jwtUtil.REFRESH_TOKEN_EXPIRATION); // 24시간


            //  Redis에 access 토큰 정보 확인 및 블랙리스트 등록
            jwtUtil.addToBlacklist(jwtUtil.getId(reissueAccessToken), jwtUtil.getExpiration(reissueAccessToken), jwtUtil.ACCESS_TOKEN_EXPIRATION);


            // RefreshToken 조회
            RefreshToken existingToken = refreshRepository.findByUser_Id(persistentUser.getId())
                    .orElseThrow(() -> new CustomAuthenticationException(AuthenticationErrorCode.MISSING_REFRESGTOKEN));

            Date expirationDate = new Date(System.currentTimeMillis() + jwtUtil.REFRESH_TOKEN_EXPIRATION);
            Date createdDate = new Date(System.currentTimeMillis());
            if (existingToken != null) {
                // 기존 토큰 업데이트
                existingToken.setExpiredAt(expirationDate);
                existingToken.setCreatedAt(createdDate);
            } else {
                // 새로운 토큰 생성
                existingToken = RefreshToken.builder()
                        .user(persistentUser)
                        .expiredAt(expirationDate)
                        .build();
            }
            refreshRepository.save(existingToken);
            // 응답 설정
            res.setHeader("Authorization", "Bearer " + reissueAccessToken); // 키 값이 같을시,  내용을 덮어씌움
            res.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.createCookie("reissue_access", reissueAccessToken, CookieUtil.ACCESS_COOKIE_EXPIRATION, req).toString()); // 키 값이 같을 시, 내용을 추가
            return true;
        } else {
            System.out.println("재 로그인 필요");
            return false;
        }

    }

    /**
     * 유저 Id 찾기
     */
    @Override
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));
    }
}

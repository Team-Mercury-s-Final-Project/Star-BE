package com.mercury.star_be.user.service;


import com.mercury.star_be.file.service.FileService;
import com.mercury.star_be.global.error.BusinessException;
import com.mercury.star_be.global.error.CustomAuthenticationException;
import com.mercury.star_be.global.error.code.AuthenticationErrorCode;
import com.mercury.star_be.global.error.code.UserErrorCode;
import com.mercury.star_be.studygroup.dto.request.GroupLeaveRequest;
import com.mercury.star_be.studygroup.service.StudyGroupService;
import com.mercury.star_be.user.Handler.CustomSuccessHandler;
import com.mercury.star_be.user.dto.request.UserRequest;
import com.mercury.star_be.user.dto.response.*;
import com.mercury.star_be.user.entity.RefreshToken;
import com.mercury.star_be.user.entity.User;
import com.mercury.star_be.user.repository.RefreshRepository;
import com.mercury.star_be.user.repository.UserRepository;
import com.mercury.star_be.user.util.JwtUtil;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mercury.star_be.global.error.code.AuthenticationErrorCode.USER_DEACTIVATED;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends DefaultOAuth2UserService implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final FileService gcsFileService;
    private final StudyGroupService studyGroupService;
    private final RefreshRepository refreshRepository;
    private final CustomSuccessHandler customSuccessHandler;
    private final EntityManager entityManager;

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

    @Override
    @Transactional
    public void deleteUserInfo(Long userId) {
        User deleteUser = userRepository.findById(userId)
                .orElseThrow(() -> new CustomAuthenticationException(AuthenticationErrorCode.USER_NOTFIND));
        deleteUser.setActive(false);
        userRepository.save(deleteUser);
    }



    @Override
    @Transactional
    public void exituserJoinGroup(GroupLeaveRequest request, HttpServletRequest httpServletReq, Authentication auth) {

        // 그룹장 위임
        User user = JwtUtil.getAuthenticatedUser(auth);
        List<GroupLeaveRequest.GroupMemberInfo> GroupLeaveRequestList = request .getGroupMemberInfos();
        if(!GroupLeaveRequestList.isEmpty()) {
            for(GroupLeaveRequest.GroupMemberInfo GroupAndMemberInfo: GroupLeaveRequestList) {
                studyGroupService.selectHost(GroupAndMemberInfo.getGroupId(), GroupAndMemberInfo.getMemberId());
            }
        }
        // 그룹 유저 삭제 및 그룹 삭제
        studyGroupService.simpleExitStudyGroup(jwtUtil.getJwt(httpServletReq));
    }





    @Override
    public boolean reissue(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws ServletException, IOException {
        String accessToken = jwtUtil.getJwt(req);
        Long userId = jwtUtil.getId(accessToken);
        RefreshToken refreshToken = refreshRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomAuthenticationException(AuthenticationErrorCode.MISSING_REFRESGTOKEN));
        boolean  test = (refreshToken.getExpiredAt()).after(new Date());
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
            // res.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.createCookie("reissue_access", reissueAccessToken, CookieUtil.ACCESS_COOKIE_EXPIRATION).toString()); // 키 값이 같을 시, 내용을 추가
            return true;
        } else {
            System.out.println("재 로그인 필요");
            return false;
        }

    }

    @Override
    public User findById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_EXIST));
    }
}

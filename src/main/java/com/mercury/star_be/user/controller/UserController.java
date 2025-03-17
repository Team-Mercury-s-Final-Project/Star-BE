package com.mercury.star_be.user.controller;

import com.mercury.star_be.global.common.ApiResponse;
import com.mercury.star_be.global.error.CustomAuthenticationException;
import com.mercury.star_be.global.error.code.AuthenticationErrorCode;
import com.mercury.star_be.studygroup.dto.request.GroupLeaveRequest;
import com.mercury.star_be.studygroup.repository.GroupMemberRepository;
import com.mercury.star_be.studygroup.service.StudyGroupServiceImpl;
import com.mercury.star_be.user.Handler.CustomSuccessHandler;
import com.mercury.star_be.user.dto.request.UserBlockRequest;
import com.mercury.star_be.user.dto.request.UserRequest;
import com.mercury.star_be.user.dto.response.BlockUserListResponse;
import com.mercury.star_be.user.dto.response.UserResponse;
import com.mercury.star_be.user.repository.RefreshRepository;
import com.mercury.star_be.user.repository.UserRepository;
import com.mercury.star_be.user.service.BlockUserService;
import com.mercury.star_be.user.service.UserService;
import com.mercury.star_be.user.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final BlockUserService blockUserService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshRepository refreshRepository;
    private final CustomSuccessHandler customSuccessHandler;
    private final StudyGroupServiceImpl studyGroupServiceImpl;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * 토큰 재 발행
     **/
    @PostMapping("/api/auth/reissue")
    public ResponseEntity<String> reissue(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws ServletException, IOException {
        try {
            if (userService.reissue(req, res, auth))
                return ResponseEntity.status(200).body("reissue Success");
            else {
                return ResponseEntity.status(401).body("reissue Fail");
            }
        } catch (Exception e) {
            throw new CustomAuthenticationException(AuthenticationErrorCode.NOTEXIST_ID_ACCESSTOKEN);
        }
    }

    @PostMapping
    @RequestMapping("/api/users")
    public ApiResponse<UserResponse> createUser(@RequestBody @Valid UserRequest userRequest) {
        UserResponse userResponse = userService.createUser(userRequest);
        return ApiResponse.success(userResponse);
    }


    /**
     * oauth 로그인 성공 후 콜백 api
     **/
    @GetMapping("/api/check-auth")
    public ResponseEntity<String> checkAuth(Authentication auth) {
        return ResponseEntity.status(200).body("Authenticated");
    }


    /**
     * 유저 정보 조회
     **/
    @GetMapping("/api/user-info")
    public ResponseEntity<Map<String, Object>> getuserInfo(Authentication auth) {
        return ResponseEntity.status(200).body(userService.getUserInfo(auth));
    }


    /**
     * 유저 정보 수정
     **/
    @PostMapping("/api/user-info")
    public ResponseEntity<String> updateUserInfo(
            Authentication auth,
            @RequestParam("nickname") String nickname,  // nickname 파라미터
            @RequestParam(value = "profileImg", required = false) MultipartFile profileImg) throws IOException {  // 이미지 파일 파라미터
        userService.updateUserInfo(auth, nickname, profileImg);
        return ResponseEntity.status(200).body("Success");
    }


    /**
     * 유저 삭제
     **/
    @DeleteMapping("/api/user-info")
    public ResponseEntity<String> deleteUserInfo(
            @RequestBody(required = false) GroupLeaveRequest request, // null 허용
            HttpServletRequest httpServletreq,
            Authentication auth
    ) {
        if (request != null) {
            userService.exituserJoinGroup(request, httpServletreq, auth);
        }
        userService.deleteUserInfo(jwtUtil.getId(jwtUtil.getJwt(httpServletreq)));
        return ResponseEntity.status(200).body("Success");
    }



    /**
     * 유저 그룹 조회
     **/
    @GetMapping("/api/user/joinGroup-info")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserJoinedGroupInfo(HttpServletRequest req, Authentication auth) {
        String token = jwtUtil.getJwt(req);
        Long userId = jwtUtil.getId(token);
        return ResponseEntity.ok(ApiResponse.success(userService.getUserJoinedInfo(userId)));
    }


    /**
     * 그룹 탈퇴
     */
    @DeleteMapping("/api/userJoinGroup")
    public ResponseEntity<String> exituserJoinGroup(@RequestBody GroupLeaveRequest request, HttpServletRequest httpServletreq, Authentication auth) {
        userService.exituserJoinGroup(request, httpServletreq, auth);
        return ResponseEntity.status(200).body("Success");
    }


    /**
     * 차단한 유저 조회
     */
    @GetMapping("/api/users/blocks")
    public ApiResponse<List<BlockUserListResponse>> getBlockUserList(Authentication auth) {
        UserResponse user = jwtUtil.getAuthenticatedUser(auth);
        List<BlockUserListResponse> blockUserList = blockUserService.getBlockUserList(user.getId());
        return ApiResponse.success(blockUserList);
    }

    /**
     * 사용자 차단
     */
    @PostMapping("/api/users/blocks")
    public ApiResponse<Void> blockUser(@RequestBody UserBlockRequest userBlockRequest, Authentication auth) {
        UserResponse user = jwtUtil.getAuthenticatedUser(auth);
        blockUserService.blockUser(user.getId(), userBlockRequest);
        return ApiResponse.success();
    }

    /**
     * 유저 1명 차단해제
     */
    @DeleteMapping("/api/users/blocks/{targetUserId}")
    public ApiResponse<Void> unblockUser(@PathVariable Long targetUserId, Authentication auth) {
        UserResponse user = JwtUtil.getAuthenticatedUser(auth);
        blockUserService.unblockUser(user.getId(), targetUserId);
        return ApiResponse.success();
    }

    /**
     * 모든 유저 차단해제
     */
    @DeleteMapping("/api/users/blocks/")
    public ApiResponse<Void> unblockAllUser(Authentication auth) {
        UserResponse user = JwtUtil.getAuthenticatedUser(auth);
        blockUserService.unblockUser(user.getId());
        return ApiResponse.success();
    }

}

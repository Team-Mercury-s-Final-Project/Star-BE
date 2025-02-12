package com.mercury.star_be.user.controller;

import com.mercury.star_be.global.common.ApiResponse;
import com.mercury.star_be.global.error.CustomAuthenticationException;
import com.mercury.star_be.global.error.code.AuthenticationErrorCode;
import com.mercury.star_be.studygroup.dto.request.GroupLeaveRequest;
import com.mercury.star_be.studygroup.dto.response.GroupMembeResponse;
import com.mercury.star_be.studygroup.dto.response.MyStudyGroupListResponse;
import com.mercury.star_be.studygroup.repository.GroupMemberRepository;
import com.mercury.star_be.studygroup.service.StudyGroupServiceImpl;
import com.mercury.star_be.user.Handler.CustomSuccessHandler;
import com.mercury.star_be.user.dto.request.UserBlockRequest;
import com.mercury.star_be.user.dto.request.UserRequest;
import com.mercury.star_be.user.dto.request.UserUnblockRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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


    //@GetMapping("/api/check-auth")
   // public ResponseEntity<String> checkAuth(Authentication auth) {
     //   if (JwtUtil.getAuthenticatedUser(auth) != null) {
     //       return ResponseEntity.status(200).body("Authenticated");
     //   }
    //    throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
   // }
    @GetMapping("/api/check-auth")
    public ResponseEntity<String> checkAuth(Authentication auth) {
            return ResponseEntity.status(200).body("Authenticated");
    }
  


    /** 유저 정보 조회 **/
    @GetMapping("/api/user-info")
    public ResponseEntity<Map<String, Object>> getuserInfo(Authentication auth) {
        if (JwtUtil.getAuthenticatedUser(auth) != null) {
            return ResponseEntity.status(200).body(userService.getUserInfo(auth));
        }
        throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
    }


    /**
     * 유저가 속한 그룹 및 그룹멤버 조회
     **/
//    @GetMapping("/api/user/joinGroup-info")
//    public ApiResponse<Map<String, Object>> getUserJoinedGroupInfo(HttpServletRequest req, Authentication auth) {
//        if (JwtUtil.getAuthenticatedUser(auth) != null) {
//
//            String token = jwtUtil.getJwt(req);
//            Map<String, Object> myGroupAndGroupMember = new HashMap<>();
//            List<MyStudyGroupListResponse> myStudyGroupList = studyGroupServiceImpl.getMyStudyGroupList(token);
//
//            // 각 그룹에 대한 정보를 Map에 담기
//            for (MyStudyGroupListResponse myStudyGroup : myStudyGroupList) {
//                List<GroupMember> groupMembers = groupMemberRepository.findByGroupIdOrderByNicknameAsc(myStudyGroup.getId());
//                myGroupAndGroupMember.put("MyGroupInfo", myStudyGroup);
//                myGroupAndGroupMember.put("MyGroupMember", groupMembers);
//            }
//            return ApiResponse.success(myGroupAndGroupMember);
//        }
//        throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
//    }


    @GetMapping("/api/user/joinGroup-info")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUserJoinedGroupInfo(HttpServletRequest req, Authentication auth) {
        if (JwtUtil.getAuthenticatedUser(auth) != null) {
            String token = jwtUtil.getJwt(req);
            Long userId = jwtUtil.getId(token);
            PageRequest pageRequest = PageRequest.of(0, 2);
            List<Map<String, Object>> groupList = new ArrayList<>();

            List<MyStudyGroupListResponse> myStudyGroupList = studyGroupServiceImpl.getMyStudyGroupList(userId);

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
                }else { // 나 혼자 있는 방
                    groupInfoMap.put("nickname", groupMemberDTOs.get(0).getNickname());
                    groupInfoMap.put("groupId", myStudyGroup.getId());
                    groupInfoMap.put("name", myStudyGroup.getName());
                    groupInfoMap.put("imageUrl", myStudyGroup.getImageUrl());
                    groupInfoMap.put("isHost", "1");
                }
                groupList.add(groupInfoMap);
            }
            return ResponseEntity.ok(ApiResponse.success(groupList));
        }
        throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
    }



    /** 유저 수정 **/
    @PostMapping("/api/user-info")
    public ResponseEntity<String> updateUserInfo(
            Authentication auth,
            @RequestParam("nickname") String nickname,  // nickname 파라미터
            @RequestParam(value = "profileImg", required = false) MultipartFile profileImg) throws IOException {  // 이미지 파일 파라미터
        if (JwtUtil.getAuthenticatedUser(auth) != null) {
            userService.updateUserInfo(auth, nickname, profileImg);
            return ResponseEntity.status(200).body("Success");
        }
        throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
    }


    /** 유저 삭제 **/
    @DeleteMapping("/api/user-info")
    public ResponseEntity<String> deleteUserInfo(
            @RequestBody(required = false) GroupLeaveRequest request, // null 허용
            HttpServletRequest httpServletreq,
            Authentication auth
    ) {
        if (JwtUtil.getAuthenticatedUser(auth) != null) {
            if (request != null) {
                userService.exituserJoinGroup(request, httpServletreq, auth);
            }
            userService.deleteUserInfo(jwtUtil.getId(jwtUtil.getJwt(httpServletreq)));
            return ResponseEntity.status(200).body("Success");
        }
        throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
    }





    @DeleteMapping("/api/userJoinGroup")
    public ResponseEntity<String> exituserJoinGroup(@RequestBody GroupLeaveRequest request, HttpServletRequest httpServletreq, Authentication auth) {
        if (JwtUtil.getAuthenticatedUser(auth) != null) {
            userService.exituserJoinGroup(request, httpServletreq, auth);
            return ResponseEntity.status(200).body("Success");
        }
        throw new CustomAuthenticationException(AuthenticationErrorCode.MISSING_ACCESSTOKEN);
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
     * 사용자 차단 해제
     */
//    @DeleteMapping("/api/users/blocks")
//    public ApiResponse<Void> unblockUser(@RequestBody UserUnblockRequest userUnblockRequest, Authentication auth) {
//        UserResponse user = jwtUtil.getAuthenticatedUser(auth);
//        blockUserService.unblockUser(user.getId(), userUnblockRequest);
//        return ApiResponse.success();
//    }

    @DeleteMapping("/api/users/blocks/{targetUserId}")
    public ApiResponse<Void> unblockUser(@PathVariable Long targetUserId, Authentication auth) {
        UserResponse user = JwtUtil.getAuthenticatedUser(auth);
        blockUserService.unblockUser(user.getId(), targetUserId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/users/blocks/")
    public ApiResponse<Void> unblockAllUser(Authentication auth) {
        UserResponse user = JwtUtil.getAuthenticatedUser(auth);
        blockUserService.unblockUser(user.getId());
        return ApiResponse.success();
    }




    /**
     * 차단 사용자 목록 조회
     */
    @GetMapping("/api/users/blocks")
    public ApiResponse<List<BlockUserListResponse>> getBlockUserList(Authentication auth) {
        UserResponse user = jwtUtil.getAuthenticatedUser(auth);
        List<BlockUserListResponse> blockUserList = blockUserService.getBlockUserList(user.getId());
        return ApiResponse.success(blockUserList);
    }
}

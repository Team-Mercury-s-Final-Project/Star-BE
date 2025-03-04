package com.mercury.star_be.user.service;

import com.mercury.star_be.studygroup.dto.request.GroupLeaveRequest;
import com.mercury.star_be.user.dto.request.UserRequest;
import com.mercury.star_be.user.dto.response.Oauth2Response;
import com.mercury.star_be.user.dto.response.UserResponse;
import com.mercury.star_be.user.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface UserService {

    UserResponse createUser(UserRequest userRequest);
    UserResponse saveUser(Oauth2Response oauth2Response, String username);
    Map<String, Object> getUserInfo(Authentication auth);
    List<Map<String, Object>> getUserJoinedInfo(Long userId );
    void updateUserInfo(Authentication auth, String nickname , MultipartFile profileImg) throws IOException;
    void deleteUserInfo(Long userId);
    void exituserJoinGroup(GroupLeaveRequest request, HttpServletRequest httpServletReq, Authentication auth);
    boolean reissue(HttpServletRequest req, HttpServletResponse res, Authentication auth) throws ServletException, IOException;
    User findById(Long userId);
}
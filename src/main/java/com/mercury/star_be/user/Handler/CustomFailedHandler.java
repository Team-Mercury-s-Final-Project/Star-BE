package com.mercury.star_be.user.Handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;

@Component
public class CustomFailedHandler implements AuthenticationFailureHandler {


    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        // 발생한 예외를 request 속성에 저장
        // request.setAttribute("javax.servlet.error.exception", exception);
        // /error 엔드포인트로 요청 포워딩 (전역 예외 처리 컨트롤러에서 처리)
        // request.getRequestDispatcher("/error").forward(request, response);

         // 401 Unauthorized 상태 코드 설정
         // response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 상태 코드

         // 에러 메시지 설정 (원하는 메시지를 여기에 추가)
         // String errorMessage = "인증 실패: " + exception.getMessage(); // 예: "탈퇴한 유저입니다."

         // 메시지를 응답 본문에 추가
         // response.getWriter().write(errorMessage);

//        String loginUrl = "http://localhost:5173/oauth2/LoginFailcallback";
//        response.sendRedirect(loginUrl);

        String errorMessage = exception.getMessage(); // 실패 메시지
        response.sendRedirect("https://mercurystudy.store/oauth2/LoginFailcallback?error=" + URLEncoder.encode(errorMessage, "UTF-8"));

    }
}

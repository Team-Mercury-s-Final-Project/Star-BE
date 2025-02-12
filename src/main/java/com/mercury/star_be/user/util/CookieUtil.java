package com.mercury.star_be.user.util;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public final static int ACCESS_COOKIE_EXPIRATION = 60 * 10;
    public final static int REFRESH_COOKIE_EXPIRATION = 60 * 60 * 24;

    public static ResponseCookie createCookie(String key, String value, Integer expiredS) {
        
        ResponseCookie cookie = ResponseCookie.from(key, value)
            // .httpOnly(true)
                .domain(".mercurystudy.store")
                .secure(true)  // HTTPS 연결에서만 전송
                .path("/")
                .maxAge(expiredS) 
                .sameSite("None") // 반드시 "None" 설정 필요
                .build();

        return  cookie;

//        Cookie cookie = new Cookie(key, value);
//        cookie.setMaxAge(expiredS);
//        cookie.setPath("/"); // 쿠키가 유효한 경로 설정
//        // cookie.setDomain("http://localhost:5173");
//        return cookie;
    }
}

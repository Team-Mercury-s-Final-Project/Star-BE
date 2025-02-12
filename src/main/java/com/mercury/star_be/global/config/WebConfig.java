package com.mercury.star_be.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**front단과 back단의 데이터 통신을 위한 설정*/
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
//            @Override
//            public void addCorsMappings(CorsRegistry registry) {
//                registry.addMapping("/**")
//                        .allowedOrigins("http://34.22.66.212:3001", "http://localhost:5173") // 프론트엔드 URL
//                        .allowedMethods("GET", "POST", "PUT", "DELETE")
//                        .allowedHeaders("*")
//                        .allowCredentials(true);
//            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // static 디렉토리 내 모든 파일을 처리
                registry.addResourceHandler("/static/**")
                        .addResourceLocations("classpath:/static/");

                // fileupload 디렉토리 내 모든 파일을 처리
                registry.addResourceHandler("/fileupload/**")
                        .addResourceLocations("classpath:/static/fileupload/");
            }

        };
    }
}

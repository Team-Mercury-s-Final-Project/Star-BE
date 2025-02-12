package com.mercury.star_be.test.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test")
public class TestController {
    /**back -> front 테스트*/
    @GetMapping("/toFront")
    public ResponseEntity<String> testData() {
        return ResponseEntity.ok("Hello front!! im backend Data~");
    }
    /**front 값 -> back 테스트*/
    @PostMapping("/toBack")
    public void testData2(
            @RequestBody String message
    ){
        System.out.println("result ::: -----" + message + "-----");
    }
    //상태체크
    @GetMapping("/healthcheck")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
    
}

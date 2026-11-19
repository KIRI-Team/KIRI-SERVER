package com.kiri.server.global.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// SecurityConfigTest용
@RestController
class SecurityTestController {

    @GetMapping("/test/security")
    public String get(){
        return "ok";
    }

    @PostMapping("/test/security")
    public String post(){
        return "ok";
    }
}

package com.web.shoppingweb.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.web.shoppingweb.dto.LoginRequestDTO;
import com.web.shoppingweb.dto.LoginResponseDTO;
import com.web.shoppingweb.service.UserService;

import jakarta.validation.Valid;



@Controller
@RequestMapping
@CrossOrigin(origins="*")
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
        LoginResponseDTO response = userService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/api/auth/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public ResponseEntity<LoginResponseDTO> loginForm(
            @RequestParam("username") String username,
            @RequestParam("password") String password
    ) {
        LoginRequestDTO loginRequest = new LoginRequestDTO(username, password);
        LoginResponseDTO response = userService.login(loginRequest);
        return ResponseEntity.ok(response);
    }
}

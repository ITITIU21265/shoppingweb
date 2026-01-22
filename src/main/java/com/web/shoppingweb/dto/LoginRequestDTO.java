package com.web.shoppingweb.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public class LoginRequestDTO {
    @NotBlank(message = "Username or email is required")
    @JsonAlias({ "email", "identifier" })
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    public LoginRequestDTO() {
    }

    public String getUsername() {
        return username;
    }
    
    public LoginRequestDTO(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

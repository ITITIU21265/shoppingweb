package com.web.shoppingweb.dto.user;

import jakarta.validation.constraints.NotBlank;

public class DeleteAccountDTO {

    @NotBlank(message = "Password is required")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

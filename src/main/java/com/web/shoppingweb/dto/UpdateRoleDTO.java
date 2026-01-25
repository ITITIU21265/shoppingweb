package com.web.shoppingweb.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateRoleDTO {

    @NotBlank(message = "Role code is required")
    private String roleCode;

    public UpdateRoleDTO() {
    }

    public UpdateRoleDTO(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}

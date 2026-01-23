package com.web.shoppingweb.dto;

import com.web.shoppingweb.entity.Role;
import jakarta.validation.constraints.NotNull;

public class UpdateRoleDTO {

    @NotNull(message = "Role is required")
    private Role role;

    public UpdateRoleDTO() {
    }

    public UpdateRoleDTO(Role role) {
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}

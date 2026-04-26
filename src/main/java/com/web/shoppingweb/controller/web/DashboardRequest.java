package com.web.shoppingweb.controller.web;

public record DashboardRequest(
        String activeView,
        String segment,
        Long customerId,
        Long orderId,
        String userQuery,
        String userRoleFilter,
        String userStatusFilter,
        int userPage
) {
}

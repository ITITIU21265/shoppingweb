package com.web.shoppingweb.controller.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.web.shoppingweb.dto.user.UserResponseDTO;
import com.web.shoppingweb.entity.order.Order;
import com.web.shoppingweb.entity.order.OrderItem;
import com.web.shoppingweb.entity.order.OrderStatus;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductVariant;
import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.service.AdminDashboardService;
import com.web.shoppingweb.service.AdminDashboardService.AdminDashboardData;

@Component
public class AdminDashboardModelBuilder {

    private static final int DASHBOARD_ORDER_LIMIT = 12;
    private static final int DASHBOARD_USER_PREVIEW_LIMIT = 6;
    private static final int MANAGED_USER_PAGE_SIZE = 10;
    private static final BigDecimal CRM_VIP_THRESHOLD = new BigDecimal("5000000");
    private static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardModelBuilder(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    public void populate(Model model, DashboardRequest request) {
        AdminDashboardData dashboardData = adminDashboardService.loadDashboard(request.orderId());
        List<UserResponseDTO> users = dashboardData.users();
        List<UserResponseDTO> customerUsers = users.stream()
                .filter(user -> "CUSTOMER".equalsIgnoreCase(user.getRole()))
                .toList();
        List<UserResponseDTO> sellerUsers = users.stream()
                .filter(user -> "SELLER".equalsIgnoreCase(user.getRole()))
                .toList();
        List<UserResponseDTO> managedUsers = users.stream()
                .filter(user -> !"ADMIN".equalsIgnoreCase(user.getRole()))
                .toList();
        Map<Long, Supplier> latestSupplierByUserId = dashboardData.latestSupplierByUserId();
        Map<Long, Supplier> pendingSupplierByUserId = dashboardData.pendingSupplierByUserId();
        Map<Long, Supplier> approvedSupplierByUserId = dashboardData.approvedSupplierByUserId();
        List<Order> allOrders = dashboardData.allOrders();
        List<Product> allProducts = dashboardData.allProducts();
        List<Product> latestProducts = dashboardData.latestProducts();
        List<ProductVariant> lowStockVariants = dashboardData.lowStockVariants();
        List<OrderItem> allOrderItems = dashboardData.allOrderItems();

        long activeManagedUsers = users.stream()
                .filter(user -> !"ADMIN".equalsIgnoreCase(user.getRole()))
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .count();
        long nonPurchasingCustomers = customerUsers.stream()
                .filter(customer -> countOrdersForCustomer(allOrders, customer.getId()) == 0)
                .count();
        long blockedManagedUsers = managedUsers.stream()
                .filter(user -> !"ACTIVE".equalsIgnoreCase(user.getStatus()))
                .count();

        BigDecimal adminTotalRevenue = allOrders.stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getSubtotalAmount)
                .filter(Objects::nonNull)
                .map(this::fromMinorAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> customerProfiles = buildCustomerProfiles(customerUsers, allOrders);
        String customerSegment = normalizeCustomerSegment(request.segment());
        List<Map<String, Object>> filteredCustomerProfiles = applyCustomerSegment(customerProfiles, customerSegment);
        String managedUserQuery = request.userQuery() == null ? "" : request.userQuery().trim();
        String managedUserRoleFilter = normalizeManagedUserRoleFilter(request.userRoleFilter());
        String managedUserStatusFilter = normalizeManagedUserStatusFilter(request.userStatusFilter());
        List<UserResponseDTO> filteredManagedUsers = applyManagedUserFilters(
                managedUsers,
                managedUserQuery,
                managedUserRoleFilter,
                managedUserStatusFilter
        );
        int managedUserTotalPages = Math.max(1,
                (int) Math.ceil((double) filteredManagedUsers.size() / MANAGED_USER_PAGE_SIZE));
        int managedUserPage = Math.min(Math.max(request.userPage(), 1), managedUserTotalPages);
        int managedUserFromIndex = filteredManagedUsers.isEmpty()
                ? 0
                : (managedUserPage - 1) * MANAGED_USER_PAGE_SIZE;
        int managedUserToIndex = filteredManagedUsers.isEmpty()
                ? 0
                : Math.min(managedUserFromIndex + MANAGED_USER_PAGE_SIZE, filteredManagedUsers.size());
        List<UserResponseDTO> pagedManagedUsers = filteredManagedUsers.isEmpty()
                ? Collections.emptyList()
                : filteredManagedUsers.subList(managedUserFromIndex, managedUserToIndex);
        int managedUserStartPage = Math.max(1, managedUserPage - 2);
        int managedUserEndPage = Math.min(managedUserTotalPages, managedUserStartPage + 4);
        managedUserStartPage = Math.max(1, managedUserEndPage - 4);
        Map<String, Object> selectedCustomerProfile = selectCustomerProfile(customerProfiles, request.customerId());
        List<Order> selectedCustomerOrders = resolveSelectedCustomerOrders(selectedCustomerProfile);
        BigDecimal customerTotalLtv = customerProfiles.stream()
                .map(profile -> (BigDecimal) profile.get("totalSpent"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageOrdersPerCustomer = customerUsers.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(allOrders.size())
                        .divide(BigDecimal.valueOf(customerUsers.size()), 1, RoundingMode.HALF_UP);

        List<Order> dashboardOrders = allOrders.stream()
                .limit(DASHBOARD_ORDER_LIMIT)
                .toList();
        Order selectedAdminOrder = dashboardData.selectedOrder();
        List<OrderItem> selectedAdminOrderItems = dashboardData.selectedOrderItems();

        model.addAttribute("adminScopeLabel", "Toan bo he thong");
        model.addAttribute("adminManagedUsers", customerUsers.size() + sellerUsers.size());
        model.addAttribute("adminActiveManagedUsers", activeManagedUsers);
        model.addAttribute("adminBlockedManagedUsers", blockedManagedUsers);
        model.addAttribute("adminTotalProducts", allProducts.size());
        model.addAttribute("adminTotalOrders", allOrders.size());
        model.addAttribute("adminTotalRevenue", adminTotalRevenue);
        model.addAttribute("adminLowStockCount", dashboardData.lowStockCount());
        model.addAttribute("adminCategoryCount", dashboardData.categoryCount());
        model.addAttribute("customerCount", customerUsers.size());
        model.addAttribute("sellerCount", sellerUsers.size());
        model.addAttribute("dashboardManagedUsers", pagedManagedUsers);
        model.addAttribute("latestSupplierByUserId", latestSupplierByUserId);
        model.addAttribute("pendingSupplierByUserId", pendingSupplierByUserId);
        model.addAttribute("pendingSuppliers", pendingSupplierByUserId.values());
        model.addAttribute("approvedSupplierByUserId", approvedSupplierByUserId);
        model.addAttribute("managedUserQuery", managedUserQuery);
        model.addAttribute("managedUserRoleFilter", managedUserRoleFilter);
        model.addAttribute("managedUserStatusFilter", managedUserStatusFilter);
        model.addAttribute("managedUserFilteredCount", filteredManagedUsers.size());
        model.addAttribute("managedUserTotalCount", managedUsers.size());
        model.addAttribute("managedUserPage", managedUserPage);
        model.addAttribute("managedUserTotalPages", managedUserTotalPages);
        model.addAttribute("managedUserHasPreviousPage", managedUserPage > 1);
        model.addAttribute("managedUserHasNextPage", managedUserPage < managedUserTotalPages);
        model.addAttribute("managedUserStartPage", managedUserStartPage);
        model.addAttribute("managedUserEndPage", managedUserEndPage);
        model.addAttribute("managedUserStartIndex", filteredManagedUsers.isEmpty() ? 0 : managedUserFromIndex + 1);
        model.addAttribute("managedUserEndIndex", managedUserToIndex);
        model.addAttribute("dashboardCustomers", filteredCustomerProfiles);
        model.addAttribute("dashboardCustomerPreview",
                customerProfiles.stream().limit(DASHBOARD_USER_PREVIEW_LIMIT).toList());
        model.addAttribute("dashboardSellerPreview",
                sellerUsers.stream().limit(DASHBOARD_USER_PREVIEW_LIMIT).toList());
        model.addAttribute("dashboardOrders", dashboardOrders);
        model.addAttribute("dashboardProducts", latestProducts);
        model.addAttribute("dashboardLowStockVariants", lowStockVariants);
        model.addAttribute("dashboardCoupons", MarketingDashboardFixtures.buildMarketingCoupons());
        model.addAttribute("dashboardAuditLogs", buildAuditLogs(users, allOrders, allProducts));
        model.addAttribute("adminRevenueSeries", buildRevenueSeries(allOrders));
        model.addAttribute("adminCategorySeries", buildCategorySeries(allOrderItems));
        model.addAttribute("customerSegment", customerSegment);
        model.addAttribute("customerTotalLtv", customerTotalLtv);
        model.addAttribute("customerAverageOrders", averageOrdersPerCustomer);
        model.addAttribute("customerNoOrderCount", nonPurchasingCustomers);
        model.addAttribute("selectedCustomerProfile", selectedCustomerProfile);
        model.addAttribute("selectedCustomerOrders", selectedCustomerOrders);
        model.addAttribute("selectedAdminOrder", selectedAdminOrder);
        model.addAttribute("selectedAdminOrderItems", selectedAdminOrderItems);
        model.addAttribute("selectedAdminOrderTimeline", buildOrderTimeline(selectedAdminOrder));
        model.addAttribute("activeAdminOrderView", request.activeView());
    }

    private String normalizeCustomerSegment(String requestedSegment) {
        if (requestedSegment == null || requestedSegment.isBlank()) {
            return "all";
        }

        String normalized = requestedSegment.trim().toLowerCase(Locale.ROOT);
        return Set.of("all", "vip", "new_no_order", "active").contains(normalized) ? normalized : "all";
    }

    private String normalizeManagedUserRoleFilter(String requestedRoleFilter) {
        if (requestedRoleFilter == null || requestedRoleFilter.isBlank()) {
            return "all";
        }

        String normalized = requestedRoleFilter.trim().toLowerCase(Locale.ROOT);
        return Set.of("all", "customer", "seller").contains(normalized) ? normalized : "all";
    }

    private String normalizeManagedUserStatusFilter(String requestedStatusFilter) {
        if (requestedStatusFilter == null || requestedStatusFilter.isBlank()) {
            return "all";
        }

        String normalized = requestedStatusFilter.trim().toLowerCase(Locale.ROOT);
        return Set.of("all", "active", "blocked", "deleted").contains(normalized) ? normalized : "all";
    }

    private List<UserResponseDTO> applyManagedUserFilters(List<UserResponseDTO> managedUsers,
                                                          String query,
                                                          String roleFilter,
                                                          String statusFilter) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return managedUsers.stream()
                .filter(user -> {
                    boolean matchesQuery = normalizedQuery.isBlank()
                            || String.valueOf(user.getId()).contains(normalizedQuery)
                            || (user.getFullName() != null && user.getFullName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                            || (user.getUsername() != null && user.getUsername().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                            || (user.getEmail() != null && user.getEmail().toLowerCase(Locale.ROOT).contains(normalizedQuery));

                    boolean matchesRole = switch (roleFilter) {
                        case "customer" -> "CUSTOMER".equalsIgnoreCase(user.getRole());
                        case "seller" -> "SELLER".equalsIgnoreCase(user.getRole());
                        default -> true;
                    };

                    boolean matchesStatus = switch (statusFilter) {
                        case "active" -> "ACTIVE".equalsIgnoreCase(user.getStatus());
                        case "blocked" -> "BLOCKED".equalsIgnoreCase(user.getStatus());
                        case "deleted" -> "DELETED".equalsIgnoreCase(user.getStatus());
                        default -> true;
                    };

                    return matchesQuery && matchesRole && matchesStatus;
                })
                .toList();
    }

    private List<Map<String, Object>> buildCustomerProfiles(List<UserResponseDTO> customerUsers, List<Order> allOrders) {
        Map<Long, List<Order>> ordersByCustomerId = new LinkedHashMap<>();
        for (Order order : allOrders) {
            if (order.getUser() != null && order.getUser().getId() != null) {
                ordersByCustomerId.computeIfAbsent(order.getUser().getId(), ignored -> new ArrayList<>()).add(order);
            }
        }

        List<Map<String, Object>> profiles = new ArrayList<>();
        for (UserResponseDTO customer : customerUsers) {
            List<Order> customerOrders = ordersByCustomerId.getOrDefault(customer.getId(), Collections.emptyList());
            BigDecimal totalSpent = customerOrders.stream()
                    .map(Order::getSubtotalAmount)
                    .filter(Objects::nonNull)
                    .map(this::fromMinorAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long orderCount = customerOrders.size();

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("user", customer);
            profile.put("orders", customerOrders);
            profile.put("orderCount", orderCount);
            profile.put("totalSpent", totalSpent);
            profile.put("averageOrderValue",
                    orderCount == 0
                            ? BigDecimal.ZERO
                            : totalSpent.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP));
            profile.put("tierLabel", resolveCustomerTierLabel(totalSpent));
            profile.put("tierTone", resolveCustomerTierTone(totalSpent));
            profile.put("hasOrders", orderCount > 0);
            profiles.add(profile);
        }

        profiles.sort(Comparator.comparing(profile -> (BigDecimal) profile.get("totalSpent"), Comparator.reverseOrder()));
        return profiles;
    }

    private List<Map<String, Object>> applyCustomerSegment(List<Map<String, Object>> customerProfiles, String segment) {
        return customerProfiles.stream()
                .filter(profile -> {
                    BigDecimal totalSpent = (BigDecimal) profile.get("totalSpent");
                    long orderCount = (long) profile.get("orderCount");

                    return switch (segment) {
                        case "vip" -> totalSpent.compareTo(CRM_VIP_THRESHOLD) > 0;
                        case "new_no_order" -> orderCount == 0;
                        case "active" -> orderCount > 0;
                        default -> true;
                    };
                })
                .toList();
    }

    private Map<String, Object> selectCustomerProfile(List<Map<String, Object>> customerProfiles, Long customerId) {
        if (customerId == null) {
            return null;
        }

        return customerProfiles.stream()
                .filter(profile -> {
                    UserResponseDTO user = (UserResponseDTO) profile.get("user");
                    return user != null && Objects.equals(user.getId(), customerId);
                })
                .findFirst()
                .orElse(null);
    }

    private List<Order> resolveSelectedCustomerOrders(Map<String, Object> selectedCustomerProfile) {
        if (selectedCustomerProfile == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Order> orders = (List<Order>) selectedCustomerProfile.get("orders");
        return orders == null ? Collections.emptyList() : orders;
    }

    private List<Map<String, Object>> buildRevenueSeries(List<Order> allOrders) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            totals.put(today.minusDays(i), BigDecimal.ZERO);
        }

        for (Order order : allOrders) {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = order.getCreatedAt().toLocalDate();
            if (totals.containsKey(date)) {
                totals.put(date, totals.get(date).add(fromMinorAmount(order.getSubtotalAmount())));
            }
        }

        BigDecimal maxAmount = totals.values().stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ONE);
        if (BigDecimal.ZERO.compareTo(maxAmount) == 0) {
            maxAmount = BigDecimal.ONE;
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : totals.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", entry.getKey().format(DateTimeFormatter.ofPattern("dd/MM")));
            point.put("amount", entry.getValue());
            point.put("heightPercent",
                    entry.getValue().multiply(BigDecimal.valueOf(100))
                            .divide(maxAmount, 0, RoundingMode.HALF_UP)
                            .intValue());
            series.add(point);
        }
        return series;
    }

    private List<Map<String, Object>> buildCategorySeries(List<OrderItem> orderItems) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (OrderItem item : orderItems) {
            if (item.getOrder() == null || item.getOrder().getStatus() == OrderStatus.CANCELLED || item.getProduct() == null) {
                continue;
            }

            String category = item.getProduct().getCategory() != null
                    ? item.getProduct().getCategory().getDisplayName()
                    : "Uncategorized";
            totals.merge(category, fromMinorAmount(item.getLineTotalAmount()), BigDecimal::add);
        }

        BigDecimal grandTotal = totals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grandTotal.compareTo(BigDecimal.ZERO) == 0) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        totals.forEach((label, amount) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", label);
            row.put("amount", amount);
            row.put("percent",
                    amount.multiply(BigDecimal.valueOf(100))
                            .divide(grandTotal, 0, RoundingMode.HALF_UP)
                            .intValue());
            categories.add(row);
        });
        categories.sort(Comparator.comparing(category -> (BigDecimal) category.get("amount"), Comparator.reverseOrder()));
        return categories;
    }

    private List<Map<String, Object>> buildAuditLogs(List<UserResponseDTO> users,
                                                     List<Order> orders,
                                                     List<Product> products) {
        List<Map<String, Object>> logs = new ArrayList<>();

        users.stream().limit(6).forEach(user -> logs.add(auditLog(
                user.getCreatedAt(),
                user.getFullName() != null ? user.getFullName() : user.getUsername(),
                user.getRole(),
                "Create Account",
                "create",
                "User",
                "Registered account @" + user.getUsername(),
                "Not captured"
        )));

        products.stream().limit(6).forEach(product -> logs.add(auditLog(
                product.getCreatedAt(),
                product.getSeller() != null
                        ? (product.getSeller().getFullName() != null
                                ? product.getSeller().getFullName()
                                : product.getSeller().getUsername())
                        : "Seller",
                "SELLER",
                "Create Product",
                "create",
                "Product",
                "Created product \"" + product.getName() + "\"",
                "Not captured"
        )));

        orders.stream().limit(6).forEach(order -> logs.add(auditLog(
                order.getCreatedAt(),
                order.getCustomerName(),
                "CUSTOMER",
                "Create Order",
                "create",
                "Order",
                "Placed order " + order.getOrderNumber() + " with status " + order.getStatus().getDisplayName(),
                "Not captured"
        )));

        logs.sort(Comparator.comparing(log -> (LocalDateTime) log.get("timestamp"), Comparator.nullsLast(Comparator.reverseOrder())));
        return logs.stream().limit(12).toList();
    }

    private Map<String, Object> auditLog(LocalDateTime timestamp,
                                         String actor,
                                         String role,
                                         String action,
                                         String actionType,
                                         String entity,
                                         String details,
                                         String ipAddress) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", timestamp);
        row.put("timestampLabel", timestamp == null ? "-" : SHORT_DATE_TIME.format(timestamp));
        row.put("actor", actor);
        row.put("role", role);
        row.put("action", action);
        row.put("actionType", actionType);
        row.put("entity", entity);
        row.put("details", details);
        row.put("ipAddress", ipAddress);
        return row;
    }

    private List<Map<String, Object>> buildOrderTimeline(Order order) {
        if (order == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(timelineStep("Order Created", order.getCreatedAt(), true));
        timeline.add(timelineStep("Confirmed", order.getStatus().ordinal() >= OrderStatus.CONFIRMED.ordinal(), order.getUpdatedAt()));
        timeline.add(timelineStep("Processing", order.getStatus().ordinal() >= OrderStatus.PROCESSING.ordinal()
                && order.getStatus() != OrderStatus.CANCELLED, order.getUpdatedAt()));
        timeline.add(timelineStep("Shipped", order.getStatus().ordinal() >= OrderStatus.SHIPPED.ordinal()
                && order.getStatus() != OrderStatus.CANCELLED, order.getUpdatedAt()));
        timeline.add(timelineStep("Delivered", order.getStatus() == OrderStatus.DELIVERED, order.getUpdatedAt()));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            timeline.add(timelineStep("Cancelled", order.getUpdatedAt(), true));
        }
        return timeline;
    }

    private Map<String, Object> timelineStep(String label, boolean reached, LocalDateTime timestamp) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("reached", reached);
        row.put("timeLabel", reached && timestamp != null ? SHORT_DATE_TIME.format(timestamp) : "Pending");
        return row;
    }

    private Map<String, Object> timelineStep(String label, LocalDateTime timestamp, boolean reached) {
        return timelineStep(label, reached, timestamp);
    }

    private long countOrdersForCustomer(List<Order> allOrders, Long customerId) {
        return allOrders.stream()
                .filter(order -> order.getUser() != null && Objects.equals(order.getUser().getId(), customerId))
                .count();
    }

    private String resolveCustomerTierLabel(BigDecimal totalSpent) {
        if (totalSpent.compareTo(new BigDecimal("10000000")) >= 0) {
            return "VIP Gold";
        }
        if (totalSpent.compareTo(CRM_VIP_THRESHOLD) > 0) {
            return "VIP Silver";
        }
        if (totalSpent.compareTo(new BigDecimal("2000000")) >= 0) {
            return "Member";
        }
        return "New";
    }

    private String resolveCustomerTierTone(BigDecimal totalSpent) {
        if (totalSpent.compareTo(new BigDecimal("10000000")) >= 0) {
            return "gold";
        }
        if (totalSpent.compareTo(CRM_VIP_THRESHOLD) > 0) {
            return "silver";
        }
        if (totalSpent.compareTo(new BigDecimal("2000000")) >= 0) {
            return "blue";
        }
        return "green";
    }

    private BigDecimal fromMinorAmount(Long amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount, 2);
    }
}

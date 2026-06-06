package com.web.shoppingweb.controller.web;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.web.shoppingweb.service.SellerDashboardService;
import com.web.shoppingweb.service.SellerDashboardService.SellerDashboardData;

@Component
public class SellerDashboardModelBuilder {

    private final SellerDashboardService sellerDashboardService;

    public SellerDashboardModelBuilder(SellerDashboardService sellerDashboardService) {
        this.sellerDashboardService = sellerDashboardService;
    }

    public void populate(Model model, String username) {
        SellerDashboardData dashboardData = sellerDashboardService.loadDashboard(username);

        model.addAttribute("sellerManagedProducts", dashboardData.managedProducts());
        model.addAttribute("sellerFeaturedProducts", dashboardData.featuredProducts());
        model.addAttribute("sellerLowStockCount", dashboardData.lowStockCount());
        model.addAttribute("sellerCategoryCount", dashboardData.categoryCount());
        model.addAttribute("sellerActiveProducts", dashboardData.activeProducts());
        model.addAttribute("sellerOrderCount", dashboardData.orderCount());
        model.addAttribute("sellerCustomerCount", dashboardData.customerCount());
        model.addAttribute("sellerRevenue", dashboardData.revenue());
        model.addAttribute("dashboardOrders", dashboardData.recentOrders());
        model.addAttribute("dashboardProducts", dashboardData.recentProducts());
        model.addAttribute("dashboardLowStockVariants", dashboardData.lowStockVariants());
        model.addAttribute("dashboardCoupons", MarketingDashboardFixtures.buildMarketingCoupons());
    }
}

package com.web.shoppingweb.controller.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MarketingDashboardFixtures {

    private MarketingDashboardFixtures() {
    }

    static List<Map<String, Object>> buildMarketingCoupons() {
        return List.of(
                coupon("FREESHIP", "Freeship", "30,000", "200,000", 1000, 342, "2026-04-01", "2026-04-30", "Active"),
                coupon("SUMMER2026", "Fixed", "50,000", "500,000", 500, 128, "2026-04-10", "2026-05-15", "Active"),
                coupon("VIP20", "Percent", "20%", "1,000,000", 200, 74, "2026-04-15", "2026-05-31", "Active"),
                coupon("NEWBUYER", "Fixed", "40,000", "300,000", 300, 41, "2026-04-20", "2026-05-20", "Draft")
        );
    }

    private static Map<String, Object> coupon(String code,
                                              String type,
                                              String value,
                                              String minOrder,
                                              int usageLimit,
                                              int usedCount,
                                              String startDate,
                                              String endDate,
                                              String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("type", type);
        row.put("value", value);
        row.put("minOrder", minOrder);
        row.put("usageLimit", usageLimit);
        row.put("usedCount", usedCount);
        row.put("usagePercent", usageLimit == 0 ? 0 : (usedCount * 100 / usageLimit));
        row.put("startDate", startDate);
        row.put("endDate", endDate);
        row.put("status", status);
        return row;
    }
}

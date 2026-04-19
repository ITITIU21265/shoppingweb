package com.web.shoppingweb.entity;

public enum ProductCategory {
    T_SHIRTS("T-Shirts"),
    JACKETS("Jackets"),
    DRESSES("Dresses"),
    SHOES("Shoes"),
    ACCESSORIES("Accessories");

    private final String displayName;

    ProductCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProductCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (ProductCategory category : values()) {
            if (category.name().equalsIgnoreCase(value)
                    || category.displayName.equalsIgnoreCase(value)) {
                return category;
            }
        }

        throw new IllegalArgumentException("Unsupported category: " + value);
    }
}

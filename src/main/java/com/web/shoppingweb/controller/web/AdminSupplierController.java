package com.web.shoppingweb.controller.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.web.shoppingweb.service.SupplierApprovalService;
import com.web.shoppingweb.service.SupplierApprovalService.SupplierReviewData;

@Controller
@RequestMapping("/admin/suppliers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupplierController {

    private static final String USER_MANAGEMENT_REDIRECT = "redirect:/dashboard?view=users";

    private final SupplierApprovalService supplierApprovalService;

    public AdminSupplierController(SupplierApprovalService supplierApprovalService) {
        this.supplierApprovalService = supplierApprovalService;
    }

    @GetMapping("/review/{userId}")
    public String review(@PathVariable Long userId, Model model) {
        SupplierReviewData reviewData = supplierApprovalService.getReviewData(userId);

        model.addAttribute("reviewUser", reviewData.user());
        model.addAttribute("supplier", reviewData.supplier());
        return "admin-supplier-review";
    }

    @PostMapping("/approve/{supplierId}")
    public String approve(@PathVariable Long supplierId, RedirectAttributes redirectAttributes) {
        supplierApprovalService.approve(supplierId);

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Supplier application approved. The user must sign in again to receive seller access.");
        return USER_MANAGEMENT_REDIRECT;
    }

    @PostMapping("/reject/{supplierId}")
    public String reject(@PathVariable Long supplierId, RedirectAttributes redirectAttributes) {
        supplierApprovalService.reject(supplierId);

        redirectAttributes.addFlashAttribute("successMessage", "Supplier application rejected.");
        return USER_MANAGEMENT_REDIRECT;
    }
}

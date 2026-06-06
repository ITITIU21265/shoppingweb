package com.web.shoppingweb.service;

import java.util.HashSet;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.entity.supplier.SupplierStatus;
import com.web.shoppingweb.entity.user.Role;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.supplier.SupplierRepository;
import com.web.shoppingweb.repository.user.RefreshTokenRepository;
import com.web.shoppingweb.repository.user.RoleRepository;
import com.web.shoppingweb.repository.user.UserRepository;

@Service
public class SupplierApprovalService {

    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public SupplierApprovalService(SupplierRepository supplierRepository,
                                   UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   RefreshTokenRepository refreshTokenRepository) {
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public SupplierReviewData getReviewData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Supplier supplier = supplierRepository.findFirstBySeller_IdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier application not found"));
        return new SupplierReviewData(user, supplier);
    }

    @Transactional
    public void approve(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier application not found"));
        Long userId = supplier.getSeller() == null ? null : supplier.getSeller().getId();
        if (userId == null) {
            throw new ResourceNotFoundException("Supplier application is not linked to a user");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        supplier.setStatus(SupplierStatus.APPROVED);
        supplierRepository.save(supplier);

        Role sellerRole = findRole("SELLER");
        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        user.getRoles().add(sellerRole);
        userRepository.save(user);
        refreshTokenRepository.deleteAllByUserValue(user);
    }

    @Transactional
    public void reject(Long supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier application not found"));
        supplier.setStatus(SupplierStatus.REJECTED);
        supplierRepository.save(supplier);
    }

    private Role findRole(String roleCode) {
        String normalizedCode = roleCode.trim().toUpperCase(Locale.ROOT);
        return roleRepository.findByCode(normalizedCode)
                .or(() -> roleRepository.findByCode("ROLE_" + normalizedCode))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + normalizedCode));
    }

    public record SupplierReviewData(User user, Supplier supplier) {
    }
}

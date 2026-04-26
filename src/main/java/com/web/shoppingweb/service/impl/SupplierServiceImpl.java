package com.web.shoppingweb.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.dto.supplier.SupplierRegistrationDTO;
import com.web.shoppingweb.entity.supplier.Supplier;
import com.web.shoppingweb.entity.supplier.SupplierStatus;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.supplier.SupplierRepository;
import com.web.shoppingweb.repository.user.UserRepository;
import com.web.shoppingweb.service.SupplierService;

@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;

    public SupplierServiceImpl(SupplierRepository supplierRepository, UserRepository userRepository) {
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findCurrentUserSupplier(String username) {
        User user = getUser(username);
        return supplierRepository.findFirstBySellerOrderByCreatedAtDesc(user);
    }

    @Override
    public Supplier register(String username, SupplierRegistrationDTO dto) {
        User user = getUser(username);
        Optional<Supplier> pendingSupplier = supplierRepository.findFirstBySellerAndStatusOrderByCreatedAtDesc(
                user,
                SupplierStatus.PENDING);

        if (pendingSupplier.isPresent()) {
            return pendingSupplier.get();
        }

        Supplier supplier = new Supplier();
        supplier.setSeller(user);
        supplier.setName(dto.getName().trim());
        supplier.setPhone(dto.getPhone().trim());
        supplier.setEmail(dto.getEmail().trim());
        supplier.setAddress(dto.getAddress().trim());
        supplier.setStatus(SupplierStatus.PENDING);
        return supplierRepository.save(supplier);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}

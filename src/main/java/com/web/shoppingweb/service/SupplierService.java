package com.web.shoppingweb.service;

import java.util.Optional;

import com.web.shoppingweb.dto.supplier.SupplierRegistrationDTO;
import com.web.shoppingweb.entity.supplier.Supplier;

public interface SupplierService {

    Optional<Supplier> findCurrentUserSupplier(String username);

    Supplier register(String username, SupplierRegistrationDTO dto);
}

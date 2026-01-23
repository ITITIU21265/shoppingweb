package com.web.shoppingweb.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.web.shoppingweb.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Integer> {
    Optional<Customer> findByEmailOrUsername(String email, String username);
}

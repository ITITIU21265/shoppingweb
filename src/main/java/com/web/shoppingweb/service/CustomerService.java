package com.web.shoppingweb.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.web.shoppingweb.dto.LoginRequestDTO;
import com.web.shoppingweb.dto.LoginResponseDTO;
import com.web.shoppingweb.entity.Customer;
import com.web.shoppingweb.repository.CustomerRepository;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    @Autowired
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        String identifier = loginRequest.getUsername();
        Customer customer = customerRepository
                .findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid credentials"
                ));

        if (!customer.getPassword().equals(loginRequest.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return new LoginResponseDTO(
                true,
                "Login successful",
                customer.getCustomerId(),
                customer.getUsername(),
                customer.getEmail()
        );
    }
}

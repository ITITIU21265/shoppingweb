package com.web.shoppingweb.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.web.shoppingweb.dto.CustomerRequestDTO;
import com.web.shoppingweb.dto.CustomerResponseDTO;
import com.web.shoppingweb.dto.CustomerUpdateDTO;
import com.web.shoppingweb.dto.LoginRequestDTO;
import com.web.shoppingweb.dto.LoginResponseDTO;

public interface CustomerService {

    //Pagination + Sorting 
    Page<CustomerResponseDTO> getAllCustomers(int page, int size, String sortBy, String sortDir);

    CustomerResponseDTO getCustomerById(Long id);

    CustomerResponseDTO createCustomer(CustomerRequestDTO requestDTO);

    CustomerResponseDTO updateCustomer(Long id, CustomerRequestDTO requestDTO);

    //PATCH
    CustomerResponseDTO partialUpdateCustomer(Long id, CustomerUpdateDTO updateDTO);

    void deleteCustomer(Long id);

    List<CustomerResponseDTO> searchCustomers(String keyword);

    List<CustomerResponseDTO> getCustomersByStatus(String status);

    // advanced search
    List<CustomerResponseDTO> advancedSearch(String name, String email, String status);

    LoginResponseDTO login(LoginRequestDTO loginRequest);
}



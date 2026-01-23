package com.web.shoppingweb.service;

import com.web.shoppingweb.dto.CustomerRequestDTO;
import com.web.shoppingweb.dto.CustomerResponseDTO;
import com.web.shoppingweb.dto.CustomerUpdateDTO;
import com.web.shoppingweb.entity.Customer;
import com.web.shoppingweb.entity.CustomerStatus;
import com.web.shoppingweb.exception.DuplicateResourceException;
import com.web.shoppingweb.exception.ResourceNotFoundException;
import com.web.shoppingweb.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service 
@Transactional 
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Autowired 
    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override // Pagination + sorting
    public Page<CustomerResponseDTO> getAllCustomers(int page, int size, String sortBy, String sortDir) {
        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        return customerRepository.findAll(pageable).map(this::convertToResponseDTO);
    }

    @Override // Get by id
    public CustomerResponseDTO getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        return convertToResponseDTO(customer);
    }

    @Override // Create
    public CustomerResponseDTO createCustomer(CustomerRequestDTO requestDTO) {
        if (customerRepository.existsByCustomerCode(requestDTO.getCustomerCode())) {
            throw new DuplicateResourceException("Customer code already exists: " + requestDTO.getCustomerCode());
        }
        if (customerRepository.existsByEmail(requestDTO.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + requestDTO.getEmail());
        }

        Customer customer = convertToEntity(requestDTO);
        Customer savedCustomer = customerRepository.save(customer);
        return convertToResponseDTO(savedCustomer);
    }

    @Override // Full update (PUT)
    public CustomerResponseDTO updateCustomer(Long id, CustomerRequestDTO requestDTO) {
        Customer existingCustomer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));

        if (!existingCustomer.getEmail().equals(requestDTO.getEmail())
                && customerRepository.existsByEmail(requestDTO.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + requestDTO.getEmail());
        }

        existingCustomer.setFullName(requestDTO.getFullName());
        existingCustomer.setEmail(requestDTO.getEmail());
        existingCustomer.setPhone(requestDTO.getPhone());
        existingCustomer.setAddress(requestDTO.getAddress());

        // Update status if provided
        if (requestDTO.getStatus() != null && !requestDTO.getStatus().trim().isEmpty()) {
            existingCustomer.setStatus(parseStatus(requestDTO.getStatus()));
        }

        Customer updatedCustomer = customerRepository.save(existingCustomer);
        return convertToResponseDTO(updatedCustomer);
    }

    @Override // Partial update (PATCH)
    public CustomerResponseDTO partialUpdateCustomer(Long id, CustomerUpdateDTO updateDTO) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));

        if (updateDTO.getFullName() != null) {
            customer.setFullName(updateDTO.getFullName());
        }

        if (updateDTO.getEmail() != null) {
            String newEmail = updateDTO.getEmail().trim();
            if (!newEmail.equalsIgnoreCase(customer.getEmail()) && customerRepository.existsByEmail(newEmail)) {
                throw new DuplicateResourceException("Email already exists: " + newEmail);
            }
            customer.setEmail(newEmail);
        }

        if (updateDTO.getPhone() != null) {
            customer.setPhone(updateDTO.getPhone());
        }

        if (updateDTO.getAddress() != null) {
            customer.setAddress(updateDTO.getAddress());
        }

        // Update status if provided
        if (updateDTO.getStatus() != null && !updateDTO.getStatus().trim().isEmpty()) {
            customer.setStatus(parseStatus(updateDTO.getStatus()));
        }

        Customer saved = customerRepository.save(customer);
        return convertToResponseDTO(saved);
    }

    @Override // Delete
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Customer not found with id: " + id);
        }
        customerRepository.deleteById(id);
    }

    @Override // Search by keyword
    public List<CustomerResponseDTO> searchCustomers(String keyword) {
        return customerRepository.searchCustomers(keyword)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override // Filter by status (ACTIVE/INACTIVE)
    public List<CustomerResponseDTO> getCustomersByStatus(String status) {
        CustomerStatus st = parseStatus(status);

        return customerRepository.findByStatus(st)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override // Advanced search with optional params
    public List<CustomerResponseDTO> advancedSearch(String name, String email, String status) {
        String n = (name == null || name.trim().isEmpty()) ? null : name.trim();
        String e = (email == null || email.trim().isEmpty()) ? null : email.trim();

        CustomerStatus st = null;
        if (status != null && !status.trim().isEmpty()) {
            st = parseStatus(status);
        }

        return customerRepository.advancedSearch(n, e, st)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // Parse enum from string
    private CustomerStatus parseStatus(String status) {
        try {
            return CustomerStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Use ACTIVE or INACTIVE.");
        }
    }

    // Entity -> Response DTO
    private CustomerResponseDTO convertToResponseDTO(Customer customer) {
        CustomerResponseDTO dto = new CustomerResponseDTO();
        dto.setId(customer.getId());
        dto.setCustomerCode(customer.getCustomerCode());
        dto.setFullName(customer.getFullName());
        dto.setEmail(customer.getEmail());
        dto.setPhone(customer.getPhone());
        dto.setAddress(customer.getAddress());
        dto.setStatus(customer.getStatus().toString());
        dto.setCreatedAt(customer.getCreatedAt());
        return dto;
    }

    // Request DTO -> Entity
    private Customer convertToEntity(CustomerRequestDTO dto) {
        Customer customer = new Customer();
        customer.setCustomerCode(dto.getCustomerCode());
        customer.setFullName(dto.getFullName());
        customer.setEmail(dto.getEmail());
        customer.setPhone(dto.getPhone());
        customer.setAddress(dto.getAddress());

        // Set status if provided
        if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
            customer.setStatus(parseStatus(dto.getStatus()));
        }

        return customer;
    }
}

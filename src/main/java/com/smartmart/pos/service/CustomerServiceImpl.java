package com.smartmart.pos.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.pos.domain.Customer;
import com.smartmart.pos.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CustomerServiceImpl implements CustomerService {

    private static final String PHONE_RULE = "\\+?\\d{6,15}";

    private final CustomerRepository customers;

    // Read-write, so the "already registered" check sees a member added a moment ago.
    @Override
    @Transactional
    public Long register(String name, String phone) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) {
            throw new CustomerRuleException("name", "Name is required");
        }
        if (cleanName.length() > 100) {
            throw new CustomerRuleException("name", "Name can have at most 100 characters");
        }
        String cleanPhone = CustomerService.normalisePhone(phone);
        if (!cleanPhone.matches(PHONE_RULE)) {
            throw new CustomerRuleException("phone", "Phone must be 6 to 15 digits");
        }
        if (customers.existsByPhone(cleanPhone)) {
            throw new CustomerRuleException("phone", "This phone number is already a member");
        }
        return customers.save(new Customer(cleanName, cleanPhone)).getId();
    }

    // Read-write, so a member registered a moment ago can be added to the sale at once.
    @Override
    @Transactional
    public Optional<CustomerSummary> findByPhone(String phone) {
        return customers.findByPhone(CustomerService.normalisePhone(phone)).map(CustomerServiceImpl::summaryOf);
    }

    @Override
    @Transactional
    public Optional<CustomerSummary> find(Long id) {
        return id == null ? Optional.empty() : customers.findById(id).map(CustomerServiceImpl::summaryOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerSummary> search(String text) {
        List<Customer> found = text == null || text.isBlank()
                ? customers.findTop50ByOrderByCreatedAtDesc()
                : customers.search(text.strip());
        return found.stream().map(CustomerServiceImpl::summaryOf).toList();
    }

    private static CustomerSummary summaryOf(Customer customer) {
        return new CustomerSummary(customer.getId(), customer.getName(), customer.getPhone(), customer.getCreatedAt());
    }
}

package com.smartmart.purchasing.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.purchasing.domain.Supplier;
import com.smartmart.purchasing.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository suppliers;

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierSummary> search(String text, Pageable pageable) {
        return suppliers.findAll(matching(text), pageable).map(SupplierServiceImpl::summaryOf);
    }

    // Reads the primary, so the form opened straight after a save is never out of date.
    @Override
    @Transactional
    public SupplierSummary get(Long id) {
        return summaryOf(find(id));
    }

    @Override
    @Transactional
    public Long create(SupplierData data) {
        String name = data.name().strip();
        if (suppliers.existsByName(name)) {
            throw new SupplierNameTakenException();
        }
        return saveChecked(new Supplier(name, cleanOptional(data.contactPerson()), data.phone().strip(),
                cleanOptional(data.email()), cleanOptional(data.address()), data.active())).getId();
    }

    @Override
    @Transactional
    public void update(Long id, SupplierData data) {
        Supplier supplier = find(id);
        String name = data.name().strip();
        if (suppliers.existsByNameAndIdNot(name, id)) {
            throw new SupplierNameTakenException();
        }
        supplier.update(name, cleanOptional(data.contactPerson()), data.phone().strip(),
                cleanOptional(data.email()), cleanOptional(data.address()), data.active());
        saveChecked(supplier);
    }

    private Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new SupplierNotFoundException(id));
    }

    // The database constraint catches two people saving the same name at once.
    private Supplier saveChecked(Supplier supplier) {
        try {
            return suppliers.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException e) {
            throw new SupplierNameTakenException();
        }
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static SupplierSummary summaryOf(Supplier supplier) {
        return new SupplierSummary(supplier.getId(), supplier.getName(), supplier.getContactPerson(),
                supplier.getPhone(), supplier.getEmail(), supplier.getAddress(), supplier.isActive());
    }

    private static Specification<Supplier> matching(String text) {
        return (root, query, builder) -> {
            if (text == null || text.isBlank()) {
                return builder.conjunction();
            }
            String pattern = "%" + escapeForLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("contactPerson")), pattern, '\\'),
                    builder.like(root.get("phone"), pattern, '\\'));
        };
    }

    private static String escapeForLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

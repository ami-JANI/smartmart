package com.smartmart.pos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.smartmart.pos.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    boolean existsByPhone(String phone);

    /** Members whose name or phone contains {@code text}, ignoring case, newest first. */
    @Query("""
            select c from Customer c
            where lower(c.name) like lower(concat('%', :text, '%')) or c.phone like concat('%', :text, '%')
            order by c.createdAt desc""")
    List<Customer> search(String text);

    List<Customer> findTop50ByOrderByCreatedAtDesc();
}

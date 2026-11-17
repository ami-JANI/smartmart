package com.smartmart.pos.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.smartmart.pos.domain.Discount;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    /** Discounts valid on {@code day}, by name. */
    @Query("select d from Discount d where d.startsOn <= :day and d.endsOn >= :day order by d.name")
    List<Discount> findActiveOn(LocalDate day);
}

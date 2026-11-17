package com.smartmart.pos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.pos.domain.Discount;
import com.smartmart.pos.repository.DiscountRepository;

@Service
class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discounts;
    private final Clock clock;

    @Autowired
    DiscountServiceImpl(DiscountRepository discounts) {
        this(discounts, Clock.systemDefaultZone());
    }

    DiscountServiceImpl(DiscountRepository discounts, Clock clock) {
        this.discounts = discounts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiscountOption> activeDiscounts() {
        return discounts.findActiveOn(today()).stream()
                .map(discount -> new DiscountOption(discount.getId(), discount.getName(), discount.description()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveDiscount> findActive(Long id) {
        LocalDate today = today();
        return discounts.findById(id)
                .filter(discount -> !today.isBefore(discount.getStartsOn()) && !today.isAfter(discount.getEndsOn()))
                .map(DiscountServiceImpl::activeOf);
    }

    private static ActiveDiscount activeOf(Discount discount) {
        return new ActiveDiscount(discount.getId(), discount.getName(), discount.strategy());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

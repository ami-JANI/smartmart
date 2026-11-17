package com.smartmart.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.pos.domain.Discount;
import com.smartmart.pos.domain.DiscountType;
import com.smartmart.pos.repository.DiscountRepository;

class DiscountServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private final DiscountRepository repository = mock(DiscountRepository.class);
    private final DiscountServiceImpl service = new DiscountServiceImpl(repository,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private Discount discount(long id, LocalDate startsOn, LocalDate endsOn) {
        Discount discount = new Discount("Staff", DiscountType.PERCENTAGE, new BigDecimal("10.00"), startsOn, endsOn);
        ReflectionTestUtils.setField(discount, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(discount));
        return discount;
    }

    @Test
    void listsTodaysDiscountsWithTheirDescription() {
        Discount staff = discount(1, TODAY, TODAY);
        when(repository.findActiveOn(TODAY)).thenReturn(List.of(staff));

        assertThat(service.activeDiscounts()).containsExactly(new DiscountOption(1L, "Staff", "10% off"));
    }

    @Test
    void findsADiscountOnItsFirstAndLastDay() {
        discount(1, TODAY, TODAY.plusDays(3));
        discount(2, TODAY.minusDays(3), TODAY);

        assertThat(service.findActive(1L)).map(ActiveDiscount::name).contains("Staff");
        assertThat(service.findActive(2L)).isPresent();
    }

    @Test
    void ignoresADiscountThatHasNotStartedOrHasEnded() {
        discount(1, TODAY.plusDays(1), TODAY.plusDays(5));
        discount(2, TODAY.minusDays(5), TODAY.minusDays(1));

        assertThat(service.findActive(1L)).isEmpty();
        assertThat(service.findActive(2L)).isEmpty();
        assertThat(service.findActive(3L)).isEmpty();
    }
}

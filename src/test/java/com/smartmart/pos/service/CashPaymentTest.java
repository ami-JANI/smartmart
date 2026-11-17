package com.smartmart.pos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Boundary values for cash: just below, exactly at and above the total (FR-POS-9). */
class CashPaymentTest {

    private final CashPayment cash = new CashPayment();

    private PaymentResult pay(String total, String received) {
        return cash.pay(new BigDecimal(total), new PaymentDetails(new BigDecimal(received)));
    }

    @Test
    void exactCashGivesNoChange() {
        PaymentResult result = pay("450.00", "450");

        assertThat(result.method()).isEqualTo("CASH");
        assertThat(result.amount()).isEqualByComparingTo("450.00");
        assertThat(result.cashReceived()).isEqualByComparingTo("450.00");
        assertThat(result.changeDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void moreCashGivesTheDifferenceAsChange() {
        assertThat(pay("450.00", "500.00").changeDue()).isEqualByComparingTo("50.00");
        assertThat(pay("0.00", "0").changeDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void cashBelowTheTotalIsRefused() {
        assertThatThrownBy(() -> pay("450.00", "449.99"))
                .isInstanceOf(PaymentRefusedException.class)
                .hasMessage("Cash received is less than the total");
    }

    @Test
    void missingCashIsRefused() {
        assertThatThrownBy(() -> cash.pay(new BigDecimal("1.00"), new PaymentDetails(null)))
                .isInstanceOf(PaymentRefusedException.class);
    }
}

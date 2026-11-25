package com.smartmart.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The State pattern on purchase orders: every allowed and refused action in the SRS states table (FR-PUR-5 to 7). */
class PurchaseOrderTest {

    private static final Long RICE_LINE = 1L;
    private static final Long OIL_LINE = 2L;

    private final Supplier supplier = new Supplier("Acme", null, "01700000000", null, null, true);

    /** A draft for 10 rice at 60.00 and 4 oil at 185.50. */
    private PurchaseOrder draft() {
        PurchaseOrder order = new PurchaseOrder(supplier, null);
        order.addLine(100L, new BigDecimal("10"), new BigDecimal("60.00"));
        order.addLine(200L, new BigDecimal("4"), new BigDecimal("185.50"));
        ReflectionTestUtils.setField(order.getLines().get(0), "id", RICE_LINE);
        ReflectionTestUtils.setField(order.getLines().get(1), "id", OIL_LINE);
        return order;
    }

    private PurchaseOrder submitted() {
        PurchaseOrder order = draft();
        order.submit();
        return order;
    }

    private PurchaseOrder partiallyReceived() {
        PurchaseOrder order = submitted();
        order.receive(Map.of(RICE_LINE, new BigDecimal("10")));
        return order;
    }

    private PurchaseOrder received() {
        PurchaseOrder order = partiallyReceived();
        order.receive(Map.of(OIL_LINE, new BigDecimal("4")));
        return order;
    }

    private static Map<Long, BigDecimal> someRice() {
        return Map.of(RICE_LINE, BigDecimal.ONE);
    }

    @Test
    void aNewOrderIsADraftWithItsTotal() {
        PurchaseOrder order = draft();

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(order.getTotal()).isEqualByComparingTo("1342.00");
        assertThat(order.getLines().get(0).getOutstanding()).isEqualByComparingTo("10");
    }

    @Test
    void aDraftCanBeSubmitted() {
        PurchaseOrder order = draft();

        order.submit();

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
    }

    @Test
    void aDraftCanBeCancelled() {
        PurchaseOrder order = draft();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void aDraftCannotBeReceivedOrClosed() {
        PurchaseOrder order = draft();

        assertThatThrownBy(() -> order.receive(someRice()))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessage("A draft purchase order cannot be received");
        assertThatThrownBy(order::close)
                .hasMessage("A draft purchase order cannot be closed");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
    }

    @Test
    void aSubmittedOrderCanBeCancelledButNotSubmittedOrClosed() {
        assertThatThrownBy(submitted()::submit).hasMessage("A submitted purchase order cannot be submitted");
        assertThatThrownBy(submitted()::close).hasMessage("A submitted purchase order cannot be closed");

        PurchaseOrder order = submitted();
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);
    }

    @Test
    void onlyADraftTakesNewLines() {
        assertThatThrownBy(() -> submitted().addLine(300L, BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessage("A submitted purchase order cannot be changed");
    }

    @Test
    void receivingPartOfTheOrderMakesItPartiallyReceived() {
        PurchaseOrder order = submitted();

        order.receive(Map.of(RICE_LINE, new BigDecimal("6")));

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(order.line(RICE_LINE).getReceivedQuantity()).isEqualByComparingTo("6");
        assertThat(order.line(RICE_LINE).getOutstanding()).isEqualByComparingTo("4");
    }

    @Test
    void receivingEverythingAtOnceMakesItReceived() {
        PurchaseOrder order = submitted();

        order.receive(Map.of(RICE_LINE, new BigDecimal("10"), OIL_LINE, new BigDecimal("4")));

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void theLastDeliveryMakesAPartiallyReceivedOrderReceived() {
        assertThat(received().getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void aPartiallyReceivedOrderCanReceiveMoreAndStayPartial() {
        PurchaseOrder order = partiallyReceived();

        order.receive(Map.of(OIL_LINE, new BigDecimal("1")));

        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    @Test
    void aPartiallyReceivedOrderCanBeClosedButNotCancelled() {
        assertThatThrownBy(partiallyReceived()::cancel)
                .hasMessage("A partially received purchase order cannot be cancelled");
        assertThatThrownBy(partiallyReceived()::submit)
                .hasMessage("A partially received purchase order cannot be submitted");

        PurchaseOrder order = partiallyReceived();
        order.close();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
    }

    @Test
    void aReceivedOrderCanOnlyBeClosed() {
        assertThatThrownBy(received()::submit).hasMessage("A received purchase order cannot be submitted");
        assertThatThrownBy(() -> received().receive(someRice()))
                .hasMessage("A received purchase order cannot be received");
        assertThatThrownBy(received()::cancel).hasMessage("A received purchase order cannot be cancelled");

        PurchaseOrder order = received();
        order.close();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
    }

    @Test
    void aClosedOrderRefusesEverything() {
        PurchaseOrder order = received();
        order.close();

        assertThatThrownBy(order::submit).hasMessage("A closed purchase order cannot be submitted");
        assertThatThrownBy(() -> order.receive(someRice())).hasMessage("A closed purchase order cannot be received");
        assertThatThrownBy(order::cancel).hasMessage("A closed purchase order cannot be cancelled");
        assertThatThrownBy(order::close).hasMessage("A closed purchase order cannot be closed");
    }

    @Test
    void aCancelledOrderRefusesEverything() {
        PurchaseOrder order = draft();
        order.cancel();

        assertThatThrownBy(order::submit).hasMessage("A cancelled purchase order cannot be submitted");
        assertThatThrownBy(() -> order.receive(someRice())).hasMessage("A cancelled purchase order cannot be received");
        assertThatThrownBy(order::cancel).hasMessage("A cancelled purchase order cannot be cancelled");
        assertThatThrownBy(order::close).hasMessage("A cancelled purchase order cannot be closed");
    }

    @Test
    void receivingMoreThanIsOutstandingIsRefusedAndNothingIsRecorded() {
        PurchaseOrder order = submitted();

        assertThatThrownBy(() -> order.receive(Map.of(RICE_LINE, new BigDecimal("2"), OIL_LINE, new BigDecimal("4.5"))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Only 4 is still to arrive on this line");

        assertThat(order.line(RICE_LINE).getReceivedQuantity()).isEqualByComparingTo("0");
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
    }

    @Test
    void receivingZeroOrNothingIsRefused() {
        assertThatThrownBy(() -> submitted().receive(Map.of(RICE_LINE, BigDecimal.ZERO)))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("A received quantity must be more than 0");
        assertThatThrownBy(() -> submitted().receive(Map.of()))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Enter the quantity received for at least one line");
    }

    @Test
    void receivingALineFromAnotherOrderIsRefused() {
        assertThatThrownBy(() -> submitted().receive(Map.of(99L, BigDecimal.ONE)))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("That line is not on this order");
    }

    @Test
    void theStateIsRebuiltFromTheStoredStatus() {
        PurchaseOrder order = submitted();
        ReflectionTestUtils.setField(order, "state", null);
        ReflectionTestUtils.setField(order, "status", PurchaseOrderStatus.RECEIVED);

        order.restoreState();

        assertThatThrownBy(order::cancel).hasMessage("A received purchase order cannot be cancelled");
        order.close();
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
    }

    @Test
    void everyStatusHasAState() {
        for (PurchaseOrderStatus status : PurchaseOrderStatus.values()) {
            assertThat(PurchaseOrderState.of(status).status()).isEqualTo(status);
        }
    }
}

package com.smartmart.purchasing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.StockBatchData;
import com.smartmart.inventory.service.StockBatchService;
import com.smartmart.purchasing.domain.IllegalStateTransitionException;
import com.smartmart.purchasing.domain.PurchaseOrder;
import com.smartmart.purchasing.domain.PurchaseOrderRuleException;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;
import com.smartmart.purchasing.domain.Supplier;
import com.smartmart.purchasing.repository.PurchaseOrderRepository;
import com.smartmart.purchasing.repository.SupplierRepository;

/**
 * Basis path tests for {@link PurchaseOrderServiceImpl#receive}, one per path in
 * docs/testing/basis-paths.md ("Purchase order receiving"), plus boundary cases.
 */
class PurchaseOrderReceivingTest {

    private static final long ORDER = 8L;
    private static final long RICE_LINE = 1L;
    private static final long OIL_LINE = 2L;
    private static final long RICE = 100L;
    private static final long OIL = 200L;
    private static final LocalDate EXPIRY = LocalDate.of(2027, 3, 31);

    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final StockBatchService stock = mock(StockBatchService.class);
    private final PurchaseOrderServiceImpl service = new PurchaseOrderServiceImpl(orders,
            mock(SupplierRepository.class), mock(ProductService.class), stock);

    private PurchaseOrder order;

    /** A submitted order for 10 kg rice at 60.00 and 4 bottles of oil at 185.50. */
    @BeforeEach
    void submittedOrder() {
        order = new PurchaseOrder(new Supplier("Acme", null, "01700000000", null, null, true), null);
        order.addLine(RICE, new BigDecimal("10"), new BigDecimal("60.00"));
        order.addLine(OIL, new BigDecimal("4"), new BigDecimal("185.50"));
        ReflectionTestUtils.setField(order.getLines().get(0), "id", RICE_LINE);
        ReflectionTestUtils.setField(order.getLines().get(1), "id", OIL_LINE);
        order.submit();
        when(orders.lockById(ORDER)).thenReturn(Optional.of(order));
    }

    private static DeliveryLineData line(long lineId, String quantity, String batchNumber) {
        return new DeliveryLineData(lineId, quantity == null ? null : new BigDecimal(quantity), batchNumber, EXPIRY);
    }

    private void assertNothingAdded() {
        verify(stock, never()).add(anyLong(), any());
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);
    }

    @Test
    void path1UnknownOrderIsNotFound() {
        assertThatThrownBy(() -> service.receive(99L, List.of(line(RICE_LINE, "1", "R-1"))))
                .isInstanceOf(PurchaseOrderNotFoundException.class);
        verify(stock, never()).add(anyLong(), any());
    }

    @Test
    void path2EmptyDeliveryIsRefused() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of()))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Enter the quantity received for at least one line");
        assertNothingAdded();
    }

    @Test
    void path3LinesWithNothingReceivedAreLeftOut() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(RICE_LINE, "0", null), line(OIL_LINE, null, ""))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Enter the quantity received for at least one line");
        assertNothingAdded();
    }

    @Test
    void path4ReceivedLineWithoutBatchNumberIsRefused() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(RICE_LINE, "2", "  "))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Enter a batch number for every line received");
        assertNothingAdded();
    }

    @Test
    void path5LineListedTwiceIsRefused() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(RICE_LINE, "2", "R-1"), line(RICE_LINE, "3", "R-2"))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("A line is listed twice in this delivery");
        assertNothingAdded();
    }

    @Test
    void path6OneLineReceivedAddsOneBatchAtTheOrderedCost() {
        service.receive(ORDER, List.of(line(RICE_LINE, "10", " R-1 "), line(OIL_LINE, "0", null)));

        verify(stock).add(RICE, new StockBatchData("R-1", new BigDecimal("10"), new BigDecimal("60.00"), EXPIRY));
        verify(stock, never()).add(eq(OIL), any());
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
    }

    @Test
    void path7EveryLineReceivedAddsABatchEachAndReceivesTheOrder() {
        service.receive(ORDER, List.of(line(RICE_LINE, "10", "R-1"), line(OIL_LINE, "4", "O-1")));

        verify(stock).add(RICE, new StockBatchData("R-1", new BigDecimal("10"), new BigDecimal("60.00"), EXPIRY));
        verify(stock).add(OIL, new StockBatchData("O-1", new BigDecimal("4"), new BigDecimal("185.50"), EXPIRY));
        assertThat(order.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
    }

    @Test
    void exactlyTheOutstandingQuantityIsAcceptedButOneMoreIsRefused() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(OIL_LINE, "5", "O-1"))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Only 4 is still to arrive on this line");
        assertNothingAdded();

        service.receive(ORDER, List.of(line(OIL_LINE, "4", "O-1")));
        assertThat(order.line(OIL_LINE).getOutstanding()).isEqualByComparingTo("0");
    }

    @Test
    void aNegativeQuantityIsRefused() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(RICE_LINE, "-1", "R-1"))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("A received quantity must be more than 0");
        assertNothingAdded();
    }

    @Test
    void aLineFromAnotherOrderIsRefused() {
        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(77L, "1", "X-1"))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("That line is not on this order");
        assertNothingAdded();
    }

    @Test
    void anOrderNotWaitingForGoodsIsRefused() {
        order.cancel();

        assertThatThrownBy(() -> service.receive(ORDER, List.of(line(RICE_LINE, "1", "R-1"))))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessage("A cancelled purchase order cannot be received");
        verify(stock, never()).add(anyLong(), any());
    }
}

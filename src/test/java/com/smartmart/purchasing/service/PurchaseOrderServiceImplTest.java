package com.smartmart.purchasing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.inventory.service.StockBatchService;
import com.smartmart.purchasing.domain.IllegalStateTransitionException;
import com.smartmart.purchasing.domain.PurchaseOrder;
import com.smartmart.purchasing.domain.PurchaseOrderRuleException;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;
import com.smartmart.purchasing.domain.Supplier;
import com.smartmart.purchasing.repository.PurchaseOrderRepository;
import com.smartmart.purchasing.repository.SupplierRepository;

class PurchaseOrderServiceImplTest {

    private static final long SUPPLIER = 3L;
    private static final long RICE = 100L;
    private static final long OIL = 200L;

    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final ProductService products = mock(ProductService.class);
    private final StockBatchService stock = mock(StockBatchService.class);
    private final PurchaseOrderServiceImpl service = new PurchaseOrderServiceImpl(orders, suppliers, products, stock);

    private Supplier supplier;

    @BeforeEach
    void setUp() {
        supplier = supplier(true);
        product(RICE, "Miniket rice", "KG", true);
        product(OIL, "Soybean oil 1L", "PIECE", true);
        when(orders.save(any())).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 9L);
            return order;
        });
    }

    private Supplier supplier(boolean active) {
        Supplier stored = new Supplier("Acme", null, "01700000000", null, null, active);
        ReflectionTestUtils.setField(stored, "id", SUPPLIER);
        when(suppliers.findById(SUPPLIER)).thenReturn(Optional.of(stored));
        return stored;
    }

    private void product(long id, String name, String saleUnit, boolean active) {
        when(products.get(id)).thenReturn(new ProductSummary(id, name, "880" + id, 1L, "Grocery",
                new BigDecimal("80.00"), saleUnit, null, active, BigDecimal.ZERO, false));
    }

    private static OrderLineData line(long productId, String quantity, String cost) {
        return new OrderLineData(productId, quantity == null ? null : new BigDecimal(quantity),
                cost == null ? null : new BigDecimal(cost));
    }

    private static PurchaseOrderData order(OrderLineData... lines) {
        return new PurchaseOrderData(SUPPLIER, "  Before Eid  ", List.of(lines));
    }

    private PurchaseOrder stored(long id, PurchaseOrderStatus status) {
        PurchaseOrder order = new PurchaseOrder(supplier, null);
        order.addLine(RICE, BigDecimal.TEN, new BigDecimal("60.00"));
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order.getLines().get(0), "id", 1L);
        if (status != PurchaseOrderStatus.DRAFT) {
            order.submit();
        }
        if (status == PurchaseOrderStatus.RECEIVED) {
            order.receive(Map.of(1L, BigDecimal.TEN));
        }
        when(orders.lockById(id)).thenReturn(Optional.of(order));
        when(orders.findById(id)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void createSavesADraftWithItsLines() {
        assertThat(service.create(order(line(RICE, "25.5", "58.00"), line(OIL, "12", "170.25")))).isEqualTo(9L);

        ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(orders).save(captor.capture());
        PurchaseOrder saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(saved.getSupplier()).isSameAs(supplier);
        assertThat(saved.getNote()).isEqualTo("Before Eid");
        assertThat(saved.getLines()).hasSize(2);
        assertThat(saved.getTotal()).isEqualByComparingTo("3522.00");
    }

    @Test
    void createRefusesAnUnknownSupplier() {
        assertThatThrownBy(() -> service.create(new PurchaseOrderData(77L, null, List.of(line(RICE, "1", "1")))))
                .isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    void createRefusesAnInactiveSupplier() {
        supplier(false);

        assertThatThrownBy(() -> service.create(order(line(RICE, "1", "1"))))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Choose an active supplier");
        verify(orders, never()).save(any());
    }

    @Test
    void createRefusesAnOrderWithoutLines() {
        assertThatThrownBy(() -> service.create(order()))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("Add at least one product");
    }

    @Test
    void createRefusesAProductListedTwice() {
        assertThatThrownBy(() -> service.create(order(line(RICE, "1", "1"), line(RICE, "2", "1"))))
                .hasMessage("Miniket rice is listed twice");
    }

    @Test
    void createRefusesAnInactiveProduct() {
        product(OIL, "Soybean oil 1L", "PIECE", false);

        assertThatThrownBy(() -> service.create(order(line(OIL, "1", "1"))))
                .hasMessage("Soybean oil 1L is not active");
    }

    @Test
    void createRefusesAQuantityOfZeroOrNone() {
        assertThatThrownBy(() -> service.create(order(line(RICE, "0", "1"))))
                .hasMessage("The quantity of Miniket rice must be more than 0");
        assertThatThrownBy(() -> service.create(order(line(RICE, null, "1"))))
                .hasMessage("The quantity of Miniket rice must be more than 0");
    }

    @Test
    void createRefusesAQuantityThatDoesNotFitTheSaleUnit() {
        assertThatThrownBy(() -> service.create(order(line(OIL, "1.5", "1"))))
                .hasMessage("The quantity of Soybean oil 1L has too many decimal places");
        assertThatThrownBy(() -> service.create(order(line(RICE, "1.0005", "1"))))
                .hasMessage("The quantity of Miniket rice has too many decimal places");
    }

    @Test
    void createAcceptsAFreeItemButRefusesABadCost() {
        assertThat(service.create(order(line(OIL, "1", "0")))).isEqualTo(9L);

        assertThatThrownBy(() -> service.create(order(line(OIL, "1", "-1"))))
                .hasMessage("The unit cost of Soybean oil 1L must be 0 or more, with at most 2 decimal places");
        assertThatThrownBy(() -> service.create(order(line(OIL, "1", "1.005"))))
                .isInstanceOf(PurchaseOrderRuleException.class);
        assertThatThrownBy(() -> service.create(order(line(OIL, "1", null))))
                .isInstanceOf(PurchaseOrderRuleException.class);
    }

    @Test
    void createRefusesANoteThatIsTooLong() {
        PurchaseOrderData data = new PurchaseOrderData(SUPPLIER, "x".repeat(256), List.of(line(RICE, "1", "1")));

        assertThatThrownBy(() -> service.create(data))
                .isInstanceOf(PurchaseOrderRuleException.class)
                .hasMessage("The note can be at most 255 characters");
    }

    @Test
    void submitCancelAndCloseGoThroughTheLockedOrder() {
        PurchaseOrder draft = stored(1L, PurchaseOrderStatus.DRAFT);
        service.submit(1L);
        assertThat(draft.getStatus()).isEqualTo(PurchaseOrderStatus.SUBMITTED);

        PurchaseOrder submitted = stored(2L, PurchaseOrderStatus.SUBMITTED);
        service.cancel(2L);
        assertThat(submitted.getStatus()).isEqualTo(PurchaseOrderStatus.CANCELLED);

        PurchaseOrder received = stored(3L, PurchaseOrderStatus.RECEIVED);
        service.close(3L);
        assertThat(received.getStatus()).isEqualTo(PurchaseOrderStatus.CLOSED);
    }

    @Test
    void anIllegalActionIsRefused() {
        stored(4L, PurchaseOrderStatus.RECEIVED);

        assertThatThrownBy(() -> service.cancel(4L))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessage("A received purchase order cannot be cancelled");
    }

    @Test
    void actingOnAnUnknownOrderIsNotFound() {
        assertThatThrownBy(() -> service.submit(5L)).isInstanceOf(PurchaseOrderNotFoundException.class);
        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(PurchaseOrderNotFoundException.class);
    }

    @Test
    void getShowsEachLineWithItsProduct() {
        stored(6L, PurchaseOrderStatus.SUBMITTED);

        PurchaseOrderDetail detail = service.get(6L);

        assertThat(detail.supplierName()).isEqualTo("Acme");
        assertThat(detail.total()).isEqualByComparingTo("600.00");
        assertThat(detail.lines()).singleElement().satisfies(view -> {
            assertThat(view.productName()).isEqualTo("Miniket rice");
            assertThat(view.barcode()).isEqualTo("880100");
            assertThat(view.outstanding()).isEqualByComparingTo("10");
        });
    }
}

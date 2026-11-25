package com.smartmart.purchasing.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.inventory.domain.SaleUnit;
import com.smartmart.inventory.service.ProductService;
import com.smartmart.inventory.service.ProductSummary;
import com.smartmart.purchasing.domain.PurchaseOrder;
import com.smartmart.purchasing.domain.PurchaseOrderLine;
import com.smartmart.purchasing.domain.PurchaseOrderRuleException;
import com.smartmart.purchasing.domain.PurchaseOrderStatus;
import com.smartmart.purchasing.domain.Supplier;
import com.smartmart.purchasing.repository.PurchaseOrderRepository;
import com.smartmart.purchasing.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final int NOTE_LENGTH = 255;

    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductService products;

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderSummary> search(PurchaseOrderStatus status, Pageable pageable) {
        Page<PurchaseOrder> page = status == null
                ? orders.findAllByOrderByIdDesc(pageable)
                : orders.findByStatusOrderByIdDesc(status, pageable);
        return page.map(order -> new PurchaseOrderSummary(order.getId(), order.getSupplier().getName(),
                order.getStatus(), order.getCreatedAt(), order.getLines().size(), order.getTotal()));
    }

    @Override
    @Transactional
    public PurchaseOrderDetail get(Long id) {
        PurchaseOrder order = orders.findById(id).orElseThrow(() -> new PurchaseOrderNotFoundException(id));
        return new PurchaseOrderDetail(order.getId(), order.getSupplier().getId(), order.getSupplier().getName(),
                order.getStatus(), order.getNote(), order.getCreatedAt(),
                order.getLines().stream().map(this::viewOf).toList(), order.getTotal());
    }

    @Override
    @Transactional
    public Long create(PurchaseOrderData data) {
        Supplier supplier = suppliers.findById(data.supplierId())
                .orElseThrow(() -> new SupplierNotFoundException(data.supplierId()));
        if (!supplier.isActive()) {
            throw new PurchaseOrderRuleException("supplierId", "Choose an active supplier");
        }
        if (data.lines() == null || data.lines().isEmpty()) {
            throw new PurchaseOrderRuleException("lines", "Add at least one product");
        }
        PurchaseOrder order = new PurchaseOrder(supplier, cleanNote(data.note()));
        Set<Long> listed = new HashSet<>();
        for (OrderLineData line : data.lines()) {
            checkLine(line, listed);
            order.addLine(line.productId(), line.quantity(), line.unitCost());
        }
        return orders.save(order).getId();
    }

    @Override
    @Transactional
    public void submit(Long id) {
        lock(id).submit();
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        lock(id).cancel();
    }

    @Override
    @Transactional
    public void close(Long id) {
        lock(id).close();
    }

    private PurchaseOrder lock(Long id) {
        return orders.lockById(id).orElseThrow(() -> new PurchaseOrderNotFoundException(id));
    }

    private void checkLine(OrderLineData line, Set<Long> listed) {
        ProductSummary product = products.get(line.productId());
        if (!listed.add(product.id())) {
            throw new PurchaseOrderRuleException("lines", product.name() + " is listed twice");
        }
        if (!product.active()) {
            throw new PurchaseOrderRuleException("lines", product.name() + " is not active");
        }
        if (!isPositive(line.quantity())) {
            throw new PurchaseOrderRuleException("quantity", "The quantity of " + product.name() + " must be more than 0");
        }
        if (!SaleUnit.valueOf(product.saleUnit()).allows(line.quantity())) {
            throw new PurchaseOrderRuleException("quantity",
                    "The quantity of " + product.name() + " has too many decimal places");
        }
        if (!isValidCost(line.unitCost())) {
            throw new PurchaseOrderRuleException("unitCost",
                    "The unit cost of " + product.name() + " must be 0 or more, with at most 2 decimal places");
        }
    }

    private static boolean isPositive(BigDecimal quantity) {
        return quantity != null && quantity.signum() > 0;
    }

    private static boolean isValidCost(BigDecimal cost) {
        return cost != null && cost.signum() >= 0 && cost.stripTrailingZeros().scale() <= 2;
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        if (note.strip().length() > NOTE_LENGTH) {
            throw new PurchaseOrderRuleException("note", "The note can be at most " + NOTE_LENGTH + " characters");
        }
        return note.strip();
    }

    private PurchaseOrderLineView viewOf(PurchaseOrderLine line) {
        ProductSummary product = products.get(line.getProductId());
        return new PurchaseOrderLineView(line.getId(), product.id(), product.name(), product.barcode(),
                line.getQuantity(), line.getUnitCost(), line.getLineTotal(), line.getReceivedQuantity(),
                line.getOutstanding());
    }
}

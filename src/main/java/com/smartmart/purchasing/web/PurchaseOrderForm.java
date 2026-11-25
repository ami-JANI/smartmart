package com.smartmart.purchasing.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.smartmart.purchasing.service.OrderLineData;
import com.smartmart.purchasing.service.PurchaseOrderData;

import lombok.Getter;
import lombok.Setter;

/**
 * The new purchase order form (SCRUM-54). It shows a few empty line rows;
 * rows without a product are ignored. The service checks every rule
 * (FR-PUR-4), so this form only carries the values.
 */
@Getter
@Setter
public class PurchaseOrderForm {

    static final int START_ROWS = 5;
    static final int MAX_ROWS = 30;

    private Long supplierId;
    private String note;
    private List<Line> lines = new ArrayList<>();

    static PurchaseOrderForm empty() {
        PurchaseOrderForm form = new PurchaseOrderForm();
        form.addRows(START_ROWS);
        return form;
    }

    void addRows(int count) {
        for (int i = 0; i < count && lines.size() < MAX_ROWS; i++) {
            lines.add(new Line());
        }
    }

    /** The rows that name a product, in order. */
    List<Line> filledLines() {
        return lines.stream().filter(line -> line.getProductId() != null).toList();
    }

    PurchaseOrderData toData() {
        return new PurchaseOrderData(supplierId, note,
                filledLines().stream().map(l -> new OrderLineData(l.getProductId(), l.getQuantity(), l.getUnitCost())).toList());
    }

    @Getter
    @Setter
    public static class Line {
        private Long productId;
        private BigDecimal quantity;
        private BigDecimal unitCost;
    }
}

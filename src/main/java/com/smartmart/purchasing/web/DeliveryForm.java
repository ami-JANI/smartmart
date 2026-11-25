package com.smartmart.purchasing.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.smartmart.purchasing.service.DeliveryLineData;
import com.smartmart.purchasing.service.PurchaseOrderDetail;
import com.smartmart.purchasing.service.PurchaseOrderLineView;

import lombok.Getter;
import lombok.Setter;

/** The receiving form (SCRUM-54, FR-PUR-6): one row per line still waiting for goods. */
@Getter
@Setter
public class DeliveryForm {

    private List<Line> lines = new ArrayList<>();

    static DeliveryForm of(PurchaseOrderDetail order) {
        DeliveryForm form = new DeliveryForm();
        for (PurchaseOrderLineView line : order.lines()) {
            if (line.outstanding().signum() > 0) {
                Line row = new Line();
                row.setLineId(line.lineId());
                form.lines.add(row);
            }
        }
        return form;
    }

    List<DeliveryLineData> toData() {
        return lines.stream()
                .map(l -> new DeliveryLineData(l.getLineId(), l.getQuantity(), l.getBatchNumber(), l.getExpiryDate()))
                .toList();
    }

    @Getter
    @Setter
    public static class Line {
        private Long lineId;
        private BigDecimal quantity;
        private String batchNumber;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate expiryDate;
    }
}

package com.smartmart.pos.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.smartmart.pos.domain.Sale;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /** Number and value of the sales in [from, to). */
    @Query(value = "select count(*) as count, coalesce(sum(total), 0) as amount from sales where sold_at >= :from and sold_at < :to",
            nativeQuery = true)
    Totals totals(Instant from, Instant to);

    /** One row per day in [from, to) that had sales; {@code day} is yyyy-MM-dd in {@code zone}. */
    @Query(value = """
            select to_char(sold_at at time zone :zone, 'YYYY-MM-DD') as day, count(*) as count, sum(total) as amount
            from sales where sold_at >= :from and sold_at < :to
            group by 1 order by 1""", nativeQuery = true)
    List<DayTotals> totalsByDay(Instant from, Instant to, String zone);

    /** Quantity and value sold per product in [from, to). */
    @Query(value = """
            select l.product_id as productId, sum(l.quantity) as quantity, sum(l.line_total) as amount
            from sale_lines l join sales s on s.id = l.sale_id
            where s.sold_at >= :from and s.sold_at < :to
            group by l.product_id""", nativeQuery = true)
    List<ProductTotals> totalsByProduct(Instant from, Instant to);

    /** Members by value bought in [from, to), largest first. */
    @Query(value = """
            select c.id as customerId, c.name as name, c.phone as phone, count(*) as count, sum(s.total) as amount
            from sales s join customers c on c.id = s.customer_id
            where s.sold_at >= :from and s.sold_at < :to
            group by c.id, c.name, c.phone
            order by amount desc, count desc limit :limit""", nativeQuery = true)
    List<CustomerTotals> topCustomers(Instant from, Instant to, int limit);

    interface Totals {
        long getCount();

        BigDecimal getAmount();
    }

    interface DayTotals {
        String getDay();

        long getCount();

        BigDecimal getAmount();
    }

    interface ProductTotals {
        Long getProductId();

        BigDecimal getQuantity();

        BigDecimal getAmount();
    }

    interface CustomerTotals {
        Long getCustomerId();

        String getName();

        String getPhone();

        long getCount();

        BigDecimal getAmount();
    }
}

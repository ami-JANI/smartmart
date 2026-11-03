package com.smartmart.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartmart.common.config.StoreSettings;
import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.domain.Product;
import com.smartmart.inventory.domain.StockBatch;
import com.smartmart.inventory.repository.CategoryRepository;
import com.smartmart.inventory.repository.ProductRepository;
import com.smartmart.inventory.repository.StockBatchRepository;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProductServiceImpl implements ProductService {

    private static final String BARCODE_TAKEN = "Barcode already in use";

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final StockBatchRepository batches;
    private final StoreSettings settings;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummary> search(String text, Long categoryId, boolean lowStockOnly, Pageable pageable) {
        Specification<Product> filter = inCategory(categoryId).and(matching(text)).and(lowOnStock(lowStockOnly));
        Page<Product> page = products.findAll(filter, pageable);
        Map<Long, BigDecimal> totals = totalsFor(page.map(Product::getId).getContent());
        return page.map(product -> summaryOf(product, totals.getOrDefault(product.getId(), BigDecimal.ZERO)));
    }

    // Reads the primary, so the page opened straight after a save is never out of date.
    @Override
    @Transactional
    public ProductSummary get(Long id) {
        Product product = find(id);
        return summaryOf(product, totalsFor(List.of(id)).getOrDefault(id, BigDecimal.ZERO));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSummary> findActiveByBarcode(String barcode) {
        return products.findByBarcodeAndActiveTrue(barcode).map(product ->
                summaryOf(product, totalsFor(List.of(product.getId())).getOrDefault(product.getId(), BigDecimal.ZERO)));
    }

    @Override
    @Transactional
    public Long create(ProductData data) {
        Category category = validCategory(data);
        requireThresholdFitsUnit(data);
        if (products.existsByBarcode(data.barcode())) {
            throw new ProductRuleException("barcode", BARCODE_TAKEN);
        }
        return saveChecked(new Product(data.name().strip(), data.barcode(), category, data.saleUnit(),
                data.price(), data.lowStockThreshold(), data.active())).getId();
    }

    @Override
    @Transactional
    public void update(Long id, ProductData data) {
        Product product = find(id);
        Category category = validCategory(data);
        requireThresholdFitsUnit(data);
        if (products.existsByBarcodeAndIdNot(data.barcode(), id)) {
            throw new ProductRuleException("barcode", BARCODE_TAKEN);
        }
        product.update(data.name().strip(), data.barcode(), category, data.saleUnit(),
                data.price(), data.lowStockThreshold(), data.active());
        saveChecked(product);
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Category validCategory(ProductData data) {
        return categories.findById(data.categoryId())
                .orElseThrow(() -> new ProductRuleException("categoryId", "Choose an existing category"));
    }

    private void requireThresholdFitsUnit(ProductData data) {
        BigDecimal threshold = data.lowStockThreshold();
        if (threshold != null && !data.saleUnit().allows(threshold)) {
            throw new ProductRuleException("lowStockThreshold", "A product sold by piece needs a whole number here");
        }
    }

    // The database constraint catches two people saving the same barcode at once.
    private Product saveChecked(Product product) {
        try {
            return products.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new ProductRuleException("barcode", BARCODE_TAKEN);
        }
    }

    private Map<Long, BigDecimal> totalsFor(List<Long> productIds) {
        return batches.totalsFor(productIds).stream()
                .collect(Collectors.toMap(StockBatchRepository.ProductTotal::getProductId,
                        StockBatchRepository.ProductTotal::getTotal));
    }

    private ProductSummary summaryOf(Product product, BigDecimal totalStock) {
        return new ProductSummary(product.getId(), product.getName(), product.getBarcode(),
                product.getCategory().getId(), product.getCategory().getName(), product.getPrice(),
                product.getSaleUnit().name(), product.getLowStockThreshold(), product.isActive(),
                totalStock, product.isLowOnStock(totalStock, settings.lowStockDefault()));
    }

    private static Specification<Product> inCategory(Long categoryId) {
        return (root, query, builder) -> categoryId == null
                ? builder.conjunction()
                : builder.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<Product> matching(String text) {
        return (root, query, builder) -> {
            if (text == null || text.isBlank()) {
                return builder.conjunction();
            }
            String pattern = "%" + escapeForLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
            return builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(root.get("barcode"), pattern, '\\'));
        };
    }

    // The same rule as Product.isLowOnStock, written for the database so paging counts stay right.
    private Specification<Product> lowOnStock(boolean lowStockOnly) {
        return (root, query, builder) -> {
            if (!lowStockOnly) {
                return builder.conjunction();
            }
            Subquery<BigDecimal> total = query.subquery(BigDecimal.class);
            Root<StockBatch> batch = total.from(StockBatch.class);
            total.select(builder.sum(batch.<BigDecimal>get("quantity")))
                    .where(builder.equal(batch.get("product"), root));
            Expression<BigDecimal> stock = builder.coalesce(total, BigDecimal.ZERO);
            Expression<BigDecimal> threshold =
                    builder.coalesce(root.<BigDecimal>get("lowStockThreshold"), settings.lowStockDefault());
            return builder.lessThanOrEqualTo(stock, threshold);
        };
    }

    private static String escapeForLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

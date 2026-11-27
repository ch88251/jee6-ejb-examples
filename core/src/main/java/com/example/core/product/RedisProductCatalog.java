package com.example.core.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;

/**
 * {@link ProductCatalog} stored in one Redis hash, {@code <prefix>products}: the
 * field is the SKU and the value is the product as JSON (for example
 * {@code {"sku":"MG-002","name":"Coffee mug","price":9.50}}).
 * <p>
 * The queries read the whole hash and filter in Java. That suits a small demo
 * catalog. A large one would keep a sorted set of prices as an index, or use a
 * search module.
 */
public class RedisProductCatalog implements ProductCatalog {

    private static final Comparator<Product> BY_SKU = Comparator.comparing(Product::getSku);
    private static final Comparator<Product> BY_PRICE =
            Comparator.comparing(Product::getPrice).thenComparing(BY_SKU);

    private final RMap<String, Product> products;

    public RedisProductCatalog(RedissonClient redisson, String keyPrefix) {
        Objects.requireNonNull(redisson, "redisson");
        this.products = redisson.getMap(keyPrefix + "products",
                new TypedJsonJacksonCodec(String.class, Product.class));
    }

    @Override
    public Product create(String sku, String name, BigDecimal price) throws DuplicateProductException {
        Product product = Product.of(sku, name, price);
        // HSETNX: only one of two concurrent creates of the same SKU succeeds.
        if (!products.fastPutIfAbsent(product.getSku(), product)) {
            throw new DuplicateProductException(product.getSku());
        }
        return product;
    }

    @Override
    public Optional<Product> find(String sku) {
        return sku == null ? Optional.empty() : Optional.ofNullable(products.get(Product.normalizeSku(sku)));
    }

    @Override
    public List<Product> findAll() {
        return sorted(products.readAllValues(), BY_SKU);
    }

    @Override
    public List<Product> findByMaxPrice(BigDecimal maxPrice) {
        Objects.requireNonNull(maxPrice, "maxPrice");
        return products.readAllValues().stream()
                .filter(p -> p.getPrice().compareTo(maxPrice) <= 0)
                .sorted(BY_PRICE)
                .collect(Collectors.toList());
    }

    /**
     * Optimistic update: read the product, compute the new price, and write it
     * only if nobody changed the product in between (a compare-and-set on the
     * hash field). If someone did, start again with the new value. Two concurrent
     * discounts therefore both apply, and neither overwrites the other.
     */
    @Override
    public Product applyDiscount(String sku, int percent) throws ProductNotFoundException {
        String key = Product.normalizeSku(sku);
        while (true) {
            Product current = key == null ? null : products.get(key);
            if (current == null) {
                throw new ProductNotFoundException(sku);
            }
            Product updated = current.withDiscount(percent);
            if (products.replace(key, current, updated)) {
                return updated;
            }
        }
    }

    @Override
    public boolean delete(String sku) {
        return sku != null && products.fastRemove(Product.normalizeSku(sku)) > 0;
    }

    @Override
    public BigDecimal getAveragePrice() {
        Collection<Product> all = products.readAllValues();
        if (all.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Product p : all) {
            total = total.add(p.getPrice());
        }
        return total.divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_EVEN);
    }

    private static List<Product> sorted(Collection<Product> values, Comparator<Product> order) {
        List<Product> list = new ArrayList<>(values);
        list.sort(order);
        return list;
    }
}

package by.regsales.repository;

import by.regsales.domain.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {
    @Query("""
            select price from ProductPrice price join fetch price.product product
            order by price.validFrom desc, product.sku, price.id
            """)
    List<ProductPrice> history();

    @Query("""
            select price from ProductPrice price where price.product.id in :productIds
            and price.validFrom <= :latestEnd and price.validTo >= :earliestStart
            """)
    List<ProductPrice> intersecting(Collection<Long> productIds, LocalDate earliestStart, LocalDate latestEnd);
}

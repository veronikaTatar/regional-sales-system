package by.regsales.repository;

import by.regsales.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByActiveTrueOrderByNameAsc();
    List<Product> findAllByOrderByNameAscIdAsc();
    Optional<Product> findByIdAndActiveTrue(Long id);
    Optional<Product> findBySkuIgnoreCase(String sku);
}

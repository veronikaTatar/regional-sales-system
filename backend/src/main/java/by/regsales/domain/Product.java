package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String unit = "шт.";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentPrice;

    private LocalDate priceAsOf;

    @Column(nullable = false)
    private boolean active = true;
}

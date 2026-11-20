package by.regsales.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "product_price_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_price_history_product_start", columnNames = {"product_id", "valid_from"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validTo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(precision = 14, scale = 2)
    private BigDecimal priceWithVat;

    @Column(precision = 14, scale = 2)
    private BigDecimal retailPrice;

    @Column(length = 50)
    private String priceListCode;
}

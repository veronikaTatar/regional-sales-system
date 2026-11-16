package by.regsales.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "roles") @Getter @Setter @NoArgsConstructor
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable = false, unique = true, length = 30) private RoleCode code;
    @Column(nullable = false, length = 100) private String name;
}

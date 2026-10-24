package com.traveleasy.bus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "template_positions", indexes = {
    @Index(name = "idx_tpl_pos_template", columnList = "template_id"),
    @Index(name = "idx_tpl_pos_unique", columnList = "template_id, position_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplatePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    @JsonIgnore
    private SeatLayoutTemplate template;

    @Column(name = "position_id", nullable = false, length = 64)
    private String positionId; // e.g. "L-R1-C0", "U-R1-C2"

    @Column(nullable = false, length = 32)
    private String seatLabel; // e.g. "1A", "L1A", "U1A"

    @Column(nullable = false, length = 32)
    private String positionType; // "SEATER" or "SLEEPER"

    @Column(nullable = false, length = 32)
    private String deck; // "LOWER", "UPPER", "SINGLE"

    @Column(name = "seat_row", nullable = false)
    private Integer rowNumber;

    @Column(name = "seat_col", nullable = false)
    private Integer columnNumber;

    @Column(length = 32)
    private String side; // "LEFT", "RIGHT", "CENTER"

    @Builder.Default
    private Boolean isWindow = false;

    @Builder.Default
    private Boolean isBookable = true;

    @Builder.Default
    private Integer rowSpan = 1;

    @Builder.Default
    private Integer colSpan = 1;

    private Integer visualOrder;
}

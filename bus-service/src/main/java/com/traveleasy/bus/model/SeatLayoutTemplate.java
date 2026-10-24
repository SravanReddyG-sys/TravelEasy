package com.traveleasy.bus.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seat_layout_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatLayoutTemplate {

    @Id
    @Column(name = "id", length = 64)
    private String id; // e.g. "2+2_SEATER", "2+1_SLEEPER"

    @Column(nullable = false)
    private String displayName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 32)
    private String busConfiguration; // "SINGLE_DECK", "DOUBLE_DECK"

    @Column(nullable = false)
    private Integer deckCount; // 1 or 2

    @Column(nullable = false)
    private Integer totalInventory;

    private Integer lowerDeckInventory;
    private Integer upperDeckInventory;

    private Integer lowerRows;
    private Integer lowerColumns;

    private Integer upperRows;
    private Integer upperColumns;

    private String referenceImage; // e.g. "2+2_SEATER.png"

    @Builder.Default
    private Integer version = 1;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("visualOrder ASC")
    @Builder.Default
    private List<TemplatePosition> positions = new ArrayList<>();
}

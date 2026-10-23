package com.traveleasy.bus;

import com.traveleasy.bus.dto.TemplateSummaryDto;
import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.model.SeatLayoutTemplate;
import com.traveleasy.bus.model.TemplatePosition;
import com.traveleasy.bus.repository.SeatLayoutTemplateRepository;
import com.traveleasy.bus.repository.TemplatePositionRepository;
import com.traveleasy.bus.service.SeatLayoutService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SeatLayoutCatalogueTest {

    @Autowired
    private SeatLayoutService seatLayoutService;

    @Autowired
    private SeatLayoutTemplateRepository templateRepository;

    @Autowired
    private TemplatePositionRepository positionRepository;

    @Test
    @DisplayName("Verify all 7 approved seat layout templates are seeded and available")
    public void testAllSevenTemplatesExist() {
        List<TemplateSummaryDto> templates = seatLayoutService.getAllTemplateSummaries();
        assertEquals(7, templates.size(), "Catalogue must contain exactly 7 approved templates");

        Set<String> ids = templates.stream().map(TemplateSummaryDto::getId).collect(Collectors.toSet());
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_2_PLUS_2_SEATER));
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER));
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_1_PLUS_1_SEATER));
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER));
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER_CUM_SLEEPER));
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_1));
        assertTrue(ids.contains(SeatLayoutService.TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_2));
    }

    @Test
    @DisplayName("Verify exact approved inventory counts for all 7 templates")
    public void testApprovedInventoryCounts() {
        // 1. 2+2 SEATER: 48 seats
        SeatLayoutTemplate t1 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_2_PLUS_2_SEATER).orElseThrow();
        assertEquals(48, t1.getTotalInventory());
        assertEquals(48, t1.getLowerDeckInventory());
        assertEquals(0, t1.getUpperDeckInventory());
        assertEquals(1, t1.getDeckCount());
        assertEquals("SINGLE_DECK", t1.getBusConfiguration());
        assertEquals(48, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t1.getId()).size());

        // 2. 2+1 SEATER: 36 seats
        SeatLayoutTemplate t2 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER).orElseThrow();
        assertEquals(36, t2.getTotalInventory());
        assertEquals(36, t2.getLowerDeckInventory());
        assertEquals(0, t2.getUpperDeckInventory());
        assertEquals(1, t2.getDeckCount());
        assertEquals("SINGLE_DECK", t2.getBusConfiguration());
        assertEquals(36, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t2.getId()).size());

        // 3. 1+1 SEATER: 24 seats
        SeatLayoutTemplate t3 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_1_PLUS_1_SEATER).orElseThrow();
        assertEquals(24, t3.getTotalInventory());
        assertEquals(24, t3.getLowerDeckInventory());
        assertEquals(0, t3.getUpperDeckInventory());
        assertEquals(1, t3.getDeckCount());
        assertEquals("SINGLE_DECK", t3.getBusConfiguration());
        assertEquals(24, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t3.getId()).size());

        // 4. 2+1 SLEEPER: 36 berths (18 Lower + 18 Upper)
        SeatLayoutTemplate t4 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER).orElseThrow();
        assertEquals(36, t4.getTotalInventory());
        assertEquals(18, t4.getLowerDeckInventory());
        assertEquals(18, t4.getUpperDeckInventory());
        assertEquals(2, t4.getDeckCount());
        assertEquals("DOUBLE_DECK", t4.getBusConfiguration());
        assertEquals(36, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t4.getId()).size());

        // 5. 2+1 SEATER_CUM_SLEEPER: 54 positions (36 Seaters Lower + 18 Sleepers Upper)
        SeatLayoutTemplate t5 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER_CUM_SLEEPER).orElseThrow();
        assertEquals(54, t5.getTotalInventory());
        assertEquals(36, t5.getLowerDeckInventory());
        assertEquals(18, t5.getUpperDeckInventory());
        assertEquals(2, t5.getDeckCount());
        assertEquals("DOUBLE_DECK", t5.getBusConfiguration());
        assertEquals(54, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t5.getId()).size());

        // 6. 2+1 HYBRID_SLEEPER-1: 48 positions (Lower: 6 berths + 24 seats = 30; Upper: 18 berths)
        SeatLayoutTemplate t6 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_1).orElseThrow();
        assertEquals(48, t6.getTotalInventory());
        assertEquals(30, t6.getLowerDeckInventory());
        assertEquals(18, t6.getUpperDeckInventory());
        assertEquals(2, t6.getDeckCount());
        assertEquals("DOUBLE_DECK", t6.getBusConfiguration());
        assertEquals(48, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t6.getId()).size());

        // 7. 2+1 HYBRID_SLEEPER-2: 42 positions (Lower: 12 seats + 12 berths = 24; Upper: 18 berths)
        SeatLayoutTemplate t7 = seatLayoutService.getTemplateById(SeatLayoutService.TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_2).orElseThrow();
        assertEquals(42, t7.getTotalInventory());
        assertEquals(24, t7.getLowerDeckInventory());
        assertEquals(18, t7.getUpperDeckInventory());
        assertEquals(2, t7.getDeckCount());
        assertEquals("DOUBLE_DECK", t7.getBusConfiguration());
        assertEquals(42, positionRepository.findByTemplate_IdOrderByVisualOrderAsc(t7.getId()).size());
    }

    @Test
    @DisplayName("Verify position uniqueness and valid seat labels within every template")
    public void testPositionUniqueness() {
        for (TemplateSummaryDto summary : seatLayoutService.getAllTemplateSummaries()) {
            List<TemplatePosition> positions = positionRepository.findByTemplate_IdOrderByVisualOrderAsc(summary.getId());
            Set<String> seenPositionIds = new HashSet<>();
            Set<String> seenLabels = new HashSet<>();

            for (TemplatePosition pos : positions) {
                assertNotNull(pos.getPositionId(), "Position ID must not be null");
                assertNotNull(pos.getSeatLabel(), "Seat label must not be null");
                assertNotNull(pos.getPositionType(), "Position type must not be null");
                assertNotNull(pos.getDeck(), "Deck must not be null");

                assertTrue(seenPositionIds.add(pos.getPositionId()),
                        String.format("Duplicate position ID '%s' found in template %s", pos.getPositionId(), summary.getId()));
                assertTrue(seenLabels.add(pos.getSeatLabel()),
                        String.format("Duplicate seat label '%s' found in template %s", pos.getSeatLabel(), summary.getId()));
            }
        }
    }

    @Test
    @DisplayName("Verify template filtering by bus configuration (Single-deck vs Double-deck)")
    public void testConfigurationFiltering() {
        List<TemplateSummaryDto> singleDecks = seatLayoutService.getTemplatesByConfiguration("SINGLE_DECK");
        assertEquals(3, singleDecks.size());
        assertTrue(singleDecks.stream().allMatch(t -> "SINGLE_DECK".equals(t.getBusConfiguration())));

        List<TemplateSummaryDto> doubleDecks = seatLayoutService.getTemplatesByConfiguration("DOUBLE_DECK");
        assertEquals(4, doubleDecks.size());
        assertTrue(doubleDecks.stream().allMatch(t -> "DOUBLE_DECK".equals(t.getBusConfiguration())));
    }

    @Test
    @DisplayName("Verify template assignment validation rejects incompatible configuration or missing template")
    public void testTemplateValidationRejections() {
        Bus bus = new Bus();

        // 1. Invalid template ID
        assertThrows(IllegalArgumentException.class, () ->
                seatLayoutService.validateAndAssignTemplate(bus, "NON_EXISTENT_TEMPLATE", "SINGLE_DECK", "AC"));

        // 2. Configuration mismatch: template is DOUBLE_DECK, but bus is SINGLE_DECK
        assertThrows(IllegalArgumentException.class, () ->
                seatLayoutService.validateAndAssignTemplate(bus, SeatLayoutService.TEMPLATE_2_PLUS_1_SLEEPER, "SINGLE_DECK", "AC"));

        // 3. Configuration mismatch: template is SINGLE_DECK, but bus is DOUBLE_DECK
        assertThrows(IllegalArgumentException.class, () ->
                seatLayoutService.validateAndAssignTemplate(bus, SeatLayoutService.TEMPLATE_2_PLUS_2_SEATER, "DOUBLE_DECK", "AC"));

        // 4. Compatible assignment succeeds and derives capacity
        SeatLayoutTemplate assigned = seatLayoutService.validateAndAssignTemplate(
                bus, SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER, "SINGLE_DECK", "NON_AC");
        assertEquals(36, bus.getTotalSeats());
        assertEquals("SINGLE_DECK", bus.getBusConfiguration());
        assertEquals("NON_AC", bus.getFacilityType());
        assertEquals(SeatLayoutService.TEMPLATE_2_PLUS_1_SEATER, bus.getTemplateId());
    }

    @Test
    @DisplayName("Verify idempotent template initialization does not duplicate records")
    public void testIdempotentInitialization() {
        long countBefore = templateRepository.count();
        assertEquals(7, countBefore);

        // Re-run initialization
        seatLayoutService.initTemplates();

        long countAfter = templateRepository.count();
        assertEquals(7, countAfter, "Re-running initTemplates must not create duplicate templates");
    }
}

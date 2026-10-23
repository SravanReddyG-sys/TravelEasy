package com.traveleasy.bus.service;

import com.traveleasy.bus.dto.*;
import com.traveleasy.bus.model.*;
import com.traveleasy.bus.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLayoutService {

    public static final String TEMPLATE_2_PLUS_2_SEATER = "2+2 SEATER";
    public static final String TEMPLATE_2_PLUS_1_SEATER = "2+1 SEATER";
    public static final String TEMPLATE_1_PLUS_1_SEATER = "1+1 SEATER";
    public static final String TEMPLATE_2_PLUS_1_SLEEPER = "2+1 SLEEPER";
    public static final String TEMPLATE_2_PLUS_1_SEATER_CUM_SLEEPER = "2+1 SEATER_CUM_SLEEPER";
    public static final String TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_1 = "2+1 HYBRID_SLEEPER-1";
    public static final String TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_2 = "2+1 HYBRID_SLEEPER-2";

    public static final String CONFIG_SINGLE_DECK = "SINGLE_DECK";
    public static final String CONFIG_DOUBLE_DECK = "DOUBLE_DECK";

    public static final String DECK_LOWER = "LOWER";
    public static final String DECK_UPPER = "UPPER";
    public static final String DECK_SINGLE = "SINGLE";

    public static final String TYPE_SEATER = "SEATER";
    public static final String TYPE_SLEEPER = "SLEEPER";

    private final SeatLayoutTemplateRepository templateRepository;
    private final TemplatePositionRepository positionRepository;
    private final BusRepository busRepository;
    private final SeatRepository seatRepository;

    @PostConstruct
    @Transactional
    public void initTemplates() {
        log.info("Checking and seeding Seat Layout Templates catalogue...");
        seedTemplateIfAbsent(build2Plus2SeaterTemplate());
        seedTemplateIfAbsent(build2Plus1SeaterTemplate());
        seedTemplateIfAbsent(build1Plus1SeaterTemplate());
        seedTemplateIfAbsent(build2Plus1SleeperTemplate());
        seedTemplateIfAbsent(build2Plus1SeaterCumSleeperTemplate());
        seedTemplateIfAbsent(build2Plus1HybridSleeper1Template());
        seedTemplateIfAbsent(build2Plus1HybridSleeper2Template());
        log.info("Seat Layout Templates catalogue initialization complete.");
    }

    private void seedTemplateIfAbsent(SeatLayoutTemplate template) {
        Optional<SeatLayoutTemplate> existing = templateRepository.findById(template.getId());
        if (existing.isEmpty()) {
            log.info("Seeding template: {} with {} positions", template.getId(), template.getTotalInventory());
            templateRepository.save(template);
        } else {
            long posCount = positionRepository.countByTemplate_Id(template.getId());
            if (posCount != template.getTotalInventory()) {
                log.info("Updating existing template positions for: {}", template.getId());
                SeatLayoutTemplate cur = existing.get();
                cur.getPositions().clear();
                for (TemplatePosition pos : template.getPositions()) {
                    pos.setTemplate(cur);
                    cur.getPositions().add(pos);
                }
                cur.setTotalInventory(template.getTotalInventory());
                cur.setLowerDeckInventory(template.getLowerDeckInventory());
                cur.setUpperDeckInventory(template.getUpperDeckInventory());
                templateRepository.save(cur);
            }
        }
    }

    // ==================== TEMPLATE BUILDERS ====================

    private SeatLayoutTemplate build2Plus2SeaterTemplate() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_2_PLUS_2_SEATER)
                .displayName("2+2 Seater (Single Deck)")
                .description("Single-decker 2+2 push-back passenger seating with 48 seats across 12 rows.")
                .busConfiguration(CONFIG_SINGLE_DECK)
                .deckCount(1)
                .totalInventory(48)
                .lowerDeckInventory(48)
                .upperDeckInventory(0)
                .lowerRows(12)
                .lowerColumns(4)
                .referenceImage("2+2_SEATER.png")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        // Row 1: Left window (col 0), Right aisle (col 2), Right window (col 3) - 3 seats
        t.getPositions().add(createPos(t, "1A", "L-R1-C0", TYPE_SEATER, DECK_SINGLE, 1, 0, "LEFT", true, true, order++));
        t.getPositions().add(createPos(t, "1B", "L-R1-C2", TYPE_SEATER, DECK_SINGLE, 1, 2, "RIGHT", false, true, order++));
        t.getPositions().add(createPos(t, "1C", "L-R1-C3", TYPE_SEATER, DECK_SINGLE, 1, 3, "RIGHT", true, true, order++));

        // Rows 2 to 11 (10 rows of 4 seats = 40 seats)
        for (int r = 2; r <= 11; r++) {
            t.getPositions().add(createPos(t, r + "A", "L-R" + r + "-C0", TYPE_SEATER, DECK_SINGLE, r, 0, "LEFT", true, true, order++));
            t.getPositions().add(createPos(t, r + "B", "L-R" + r + "-C1", TYPE_SEATER, DECK_SINGLE, r, 1, "LEFT", false, true, order++));
            t.getPositions().add(createPos(t, r + "C", "L-R" + r + "-C2", TYPE_SEATER, DECK_SINGLE, r, 2, "RIGHT", false, true, order++));
            t.getPositions().add(createPos(t, r + "D", "L-R" + r + "-C3", TYPE_SEATER, DECK_SINGLE, r, 3, "RIGHT", true, true, order++));
        }

        // Row 12 (back row of 5 seats = 5 seats)
        t.getPositions().add(createPos(t, "12A", "L-R12-C0", TYPE_SEATER, DECK_SINGLE, 12, 0, "LEFT", true, true, order++));
        t.getPositions().add(createPos(t, "12B", "L-R12-C1", TYPE_SEATER, DECK_SINGLE, 12, 1, "LEFT", false, true, order++));
        t.getPositions().add(createPos(t, "12C", "L-R12-C2", TYPE_SEATER, DECK_SINGLE, 12, 2, "CENTER", false, true, order++));
        t.getPositions().add(createPos(t, "12D", "L-R12-C3", TYPE_SEATER, DECK_SINGLE, 12, 3, "RIGHT", false, true, order++));
        t.getPositions().add(createPos(t, "12E", "L-R12-C4", TYPE_SEATER, DECK_SINGLE, 12, 4, "RIGHT", true, true, order++));

        return t;
    }

    private SeatLayoutTemplate build2Plus1SeaterTemplate() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_2_PLUS_1_SEATER)
                .displayName("2+1 Seater (Single Deck)")
                .description("Single-decker 2+1 luxury semi-sleeper seating with 36 passenger seats across 12 rows.")
                .busConfiguration(CONFIG_SINGLE_DECK)
                .deckCount(1)
                .totalInventory(36)
                .lowerDeckInventory(36)
                .upperDeckInventory(0)
                .lowerRows(12)
                .lowerColumns(4)
                .referenceImage("2+1_SEATER.png")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        for (int r = 1; r <= 12; r++) {
            t.getPositions().add(createPos(t, r + "A", "L-R" + r + "-C0", TYPE_SEATER, DECK_SINGLE, r, 0, "LEFT", true, true, order++));
            t.getPositions().add(createPos(t, r + "B", "L-R" + r + "-C2", TYPE_SEATER, DECK_SINGLE, r, 2, "RIGHT", false, true, order++));
            t.getPositions().add(createPos(t, r + "C", "L-R" + r + "-C3", TYPE_SEATER, DECK_SINGLE, r, 3, "RIGHT", true, true, order++));
        }
        return t;
    }

    private SeatLayoutTemplate build1Plus1SeaterTemplate() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_1_PLUS_1_SEATER)
                .displayName("1+1 Seater (Single Deck)")
                .description("Single-decker 1+1 ultra luxury VIP passenger seating with 24 individual seats across 12 rows.")
                .busConfiguration(CONFIG_SINGLE_DECK)
                .deckCount(1)
                .totalInventory(24)
                .lowerDeckInventory(24)
                .upperDeckInventory(0)
                .lowerRows(12)
                .lowerColumns(3)
                .referenceImage("1+1_SEATER.png")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        for (int r = 1; r <= 12; r++) {
            t.getPositions().add(createPos(t, r + "A", "L-R" + r + "-C0", TYPE_SEATER, DECK_SINGLE, r, 0, "LEFT", true, true, order++));
            t.getPositions().add(createPos(t, r + "B", "L-R" + r + "-C2", TYPE_SEATER, DECK_SINGLE, r, 2, "RIGHT", true, true, order++));
        }
        return t;
    }

    private SeatLayoutTemplate build2Plus1SleeperTemplate() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_2_PLUS_1_SLEEPER)
                .displayName("2+1 Sleeper (Double Deck)")
                .description("Double-decker all-sleeper layout with 18 berths on lower deck and 18 berths on upper deck (36 berths total).")
                .busConfiguration(CONFIG_DOUBLE_DECK)
                .deckCount(2)
                .totalInventory(36)
                .lowerDeckInventory(18)
                .upperDeckInventory(18)
                .lowerRows(6)
                .lowerColumns(4)
                .upperRows(6)
                .upperColumns(4)
                .referenceImage("2+1_SLEEPER.png")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        // Lower Deck (6 rows x 3 berths = 18 berths)
        for (int r = 1; r <= 6; r++) {
            t.getPositions().add(createPos(t, "L" + r + "A", "L-R" + r + "-C0", TYPE_SLEEPER, DECK_LOWER, r, 0, "LEFT", true, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "L" + r + "B", "L-R" + r + "-C2", TYPE_SLEEPER, DECK_LOWER, r, 2, "RIGHT", false, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "L" + r + "C", "L-R" + r + "-C3", TYPE_SLEEPER, DECK_LOWER, r, 3, "RIGHT", true, true, 2, 1, order++));
        }

        // Upper Deck (6 rows x 3 berths = 18 berths)
        for (int r = 1; r <= 6; r++) {
            t.getPositions().add(createPos(t, "U" + r + "A", "U-R" + r + "-C0", TYPE_SLEEPER, DECK_UPPER, r, 0, "LEFT", true, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "B", "U-R" + r + "-C2", TYPE_SLEEPER, DECK_UPPER, r, 2, "RIGHT", false, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "C", "U-R" + r + "-C3", TYPE_SLEEPER, DECK_UPPER, r, 3, "RIGHT", true, true, 2, 1, order++));
        }
        return t;
    }

    private SeatLayoutTemplate build2Plus1SeaterCumSleeperTemplate() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_2_PLUS_1_SEATER_CUM_SLEEPER)
                .displayName("2+1 Seater cum Sleeper (Double Deck)")
                .description("Double-decker with lower deck 1+2 seater (36 seats, 12 rows) and upper deck 1+2 sleeper (18 berths, 6 rows). Total 54 positions.")
                .busConfiguration(CONFIG_DOUBLE_DECK)
                .deckCount(2)
                .totalInventory(54)
                .lowerDeckInventory(36)
                .upperDeckInventory(18)
                .lowerRows(12)
                .lowerColumns(4)
                .upperRows(6)
                .upperColumns(4)
                .referenceImage("2+1_SEATER_CUM_SLEEPER.png")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        // Lower Deck: 12 rows of 3 seats = 36 seats
        for (int r = 1; r <= 12; r++) {
            t.getPositions().add(createPos(t, "L" + r + "A", "L-R" + r + "-C0", TYPE_SEATER, DECK_LOWER, r, 0, "LEFT", true, true, order++));
            t.getPositions().add(createPos(t, "L" + r + "B", "L-R" + r + "-C2", TYPE_SEATER, DECK_LOWER, r, 2, "RIGHT", false, true, order++));
            t.getPositions().add(createPos(t, "L" + r + "C", "L-R" + r + "-C3", TYPE_SEATER, DECK_LOWER, r, 3, "RIGHT", true, true, order++));
        }

        // Upper Deck: 6 rows of 3 berths = 18 berths
        for (int r = 1; r <= 6; r++) {
            t.getPositions().add(createPos(t, "U" + r + "A", "U-R" + r + "-C0", TYPE_SLEEPER, DECK_UPPER, r, 0, "LEFT", true, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "B", "U-R" + r + "-C2", TYPE_SLEEPER, DECK_UPPER, r, 2, "RIGHT", false, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "C", "U-R" + r + "-C3", TYPE_SLEEPER, DECK_UPPER, r, 3, "RIGHT", true, true, 2, 1, order++));
        }
        return t;
    }

    private SeatLayoutTemplate build2Plus1HybridSleeper1Template() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_1)
                .displayName("2+1 Hybrid Sleeper-1 (Double Deck)")
                .description("Double-decker with lower deck 6 sleeper berths + 24 seats (30 positions) and upper deck 18 sleeper berths. Total 48 positions.")
                .busConfiguration(CONFIG_DOUBLE_DECK)
                .deckCount(2)
                .totalInventory(48)
                .lowerDeckInventory(30)
                .upperDeckInventory(18)
                .lowerRows(12)
                .lowerColumns(4)
                .upperRows(6)
                .upperColumns(4)
                .referenceImage("2+1 HYBRID_SLEEPER-1.PNG")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        // Lower Deck Left: 6 sleeper berths (spanning 2 rows each: 1-2, 3-4, 5-6, 7-8, 9-10, 11-12)
        for (int b = 1; b <= 6; b++) {
            int startRow = (b - 1) * 2 + 1;
            t.getPositions().add(createPos(t, "L" + b + "A", "L-R" + startRow + "-C0", TYPE_SLEEPER, DECK_LOWER, startRow, 0, "LEFT", true, true, 2, 1, order++));
        }

        // Lower Deck Right: 12 rows of 2 seaters (cols 2 and 3) = 24 seats
        for (int r = 1; r <= 12; r++) {
            t.getPositions().add(createPos(t, "L" + r + "B", "L-R" + r + "-C2", TYPE_SEATER, DECK_LOWER, r, 2, "RIGHT", false, true, order++));
            t.getPositions().add(createPos(t, "L" + r + "C", "L-R" + r + "-C3", TYPE_SEATER, DECK_LOWER, r, 3, "RIGHT", true, true, order++));
        }

        // Upper Deck: 6 rows of 3 sleepers = 18 berths
        for (int r = 1; r <= 6; r++) {
            t.getPositions().add(createPos(t, "U" + r + "A", "U-R" + r + "-C0", TYPE_SLEEPER, DECK_UPPER, r, 0, "LEFT", true, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "B", "U-R" + r + "-C2", TYPE_SLEEPER, DECK_UPPER, r, 2, "RIGHT", false, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "C", "U-R" + r + "-C3", TYPE_SLEEPER, DECK_UPPER, r, 3, "RIGHT", true, true, 2, 1, order++));
        }
        return t;
    }

    private SeatLayoutTemplate build2Plus1HybridSleeper2Template() {
        SeatLayoutTemplate t = SeatLayoutTemplate.builder()
                .id(TEMPLATE_2_PLUS_1_HYBRID_SLEEPER_2)
                .displayName("2+1 Hybrid Sleeper-2 (Double Deck)")
                .description("Double-decker with lower deck 12 single seats + 12 sleeper berths (24 positions) and upper deck 18 sleeper berths. Total 42 positions.")
                .busConfiguration(CONFIG_DOUBLE_DECK)
                .deckCount(2)
                .totalInventory(42)
                .lowerDeckInventory(24)
                .upperDeckInventory(18)
                .lowerRows(12)
                .lowerColumns(4)
                .upperRows(6)
                .upperColumns(4)
                .referenceImage("2+1 HYBRID_SLEEPER-2.png")
                .version(1)
                .positions(new ArrayList<>())
                .build();

        int order = 1;
        // Lower Deck Left: 12 single seats (col 0) = 12 seats
        for (int r = 1; r <= 12; r++) {
            t.getPositions().add(createPos(t, "L" + r + "A", "L-R" + r + "-C0", TYPE_SEATER, DECK_LOWER, r, 0, "LEFT", true, true, order++));
        }

        // Lower Deck Right: 6 double sleepers (cols 2 and 3, spanning 2 rows each) = 12 berths
        for (int b = 1; b <= 6; b++) {
            int startRow = (b - 1) * 2 + 1;
            t.getPositions().add(createPos(t, "L" + b + "B", "L-R" + startRow + "-C2", TYPE_SLEEPER, DECK_LOWER, startRow, 2, "RIGHT", false, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "L" + b + "C", "L-R" + startRow + "-C3", TYPE_SLEEPER, DECK_LOWER, startRow, 3, "RIGHT", true, true, 2, 1, order++));
        }

        // Upper Deck: 6 rows of 3 sleepers = 18 berths
        for (int r = 1; r <= 6; r++) {
            t.getPositions().add(createPos(t, "U" + r + "A", "U-R" + r + "-C0", TYPE_SLEEPER, DECK_UPPER, r, 0, "LEFT", true, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "B", "U-R" + r + "-C2", TYPE_SLEEPER, DECK_UPPER, r, 2, "RIGHT", false, true, 2, 1, order++));
            t.getPositions().add(createPos(t, "U" + r + "C", "U-R" + r + "-C3", TYPE_SLEEPER, DECK_UPPER, r, 3, "RIGHT", true, true, 2, 1, order++));
        }
        return t;
    }

    private TemplatePosition createPos(SeatLayoutTemplate t, String seatLabel, String positionId,
                                      String posType, String deck, int row, int col,
                                      String side, boolean isWindow, boolean isBookable, int visualOrder) {
        return createPos(t, seatLabel, positionId, posType, deck, row, col, side, isWindow, isBookable, 1, 1, visualOrder);
    }

    private TemplatePosition createPos(SeatLayoutTemplate t, String seatLabel, String positionId,
                                      String posType, String deck, int row, int col,
                                      String side, boolean isWindow, boolean isBookable,
                                      int rowSpan, int colSpan, int visualOrder) {
        return TemplatePosition.builder()
                .template(t)
                .seatLabel(seatLabel)
                .positionId(positionId)
                .positionType(posType)
                .deck(deck)
                .rowNumber(row)
                .columnNumber(col)
                .side(side)
                .isWindow(isWindow)
                .isBookable(isBookable)
                .rowSpan(rowSpan)
                .colSpan(colSpan)
                .visualOrder(visualOrder)
                .build();
    }

    // ==================== QUERY METHODS ====================

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> getAllTemplateSummaries() {
        return templateRepository.findAll().stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TemplateSummaryDto> getTemplatesByConfiguration(String configuration) {
        return templateRepository.findByBusConfiguration(configuration).stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<SeatLayoutTemplate> getTemplateById(String templateId) {
        return templateRepository.findById(templateId);
    }

    private TemplateSummaryDto toSummaryDto(SeatLayoutTemplate t) {
        return TemplateSummaryDto.builder()
                .id(t.getId())
                .displayName(t.getDisplayName())
                .description(t.getDescription())
                .busConfiguration(t.getBusConfiguration())
                .deckCount(t.getDeckCount())
                .totalInventory(t.getTotalInventory())
                .lowerDeckInventory(t.getLowerDeckInventory())
                .upperDeckInventory(t.getUpperDeckInventory())
                .lowerRows(t.getLowerRows())
                .lowerColumns(t.getLowerColumns())
                .upperRows(t.getUpperRows())
                .upperColumns(t.getUpperColumns())
                .referenceImage(t.getReferenceImage())
                .version(t.getVersion())
                .build();
    }

    // ==================== BUS & INVENTORY OPERATIONS ====================

    /**
     * Validates and applies a template to a bus. Returns the validated template.
     */
    public SeatLayoutTemplate validateAndAssignTemplate(Bus bus, String templateId, String busConfiguration, String facilityType) {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new IllegalArgumentException("Template ID must be specified");
        }

        SeatLayoutTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid seat layout template ID: " + templateId));

        if (busConfiguration != null && !busConfiguration.trim().isEmpty()) {
            if (!template.getBusConfiguration().equalsIgnoreCase(busConfiguration.trim())) {
                throw new IllegalArgumentException(String.format("Template '%s' requires configuration '%s', but bus has '%s'",
                        templateId, template.getBusConfiguration(), busConfiguration));
            }
        }

        bus.setTemplateId(template.getId());
        bus.setBusConfiguration(template.getBusConfiguration());
        bus.setTotalSeats(template.getTotalInventory());
        if (facilityType != null && !facilityType.trim().isEmpty()) {
            bus.setFacilityType(facilityType.trim().toUpperCase());
        } else if (bus.getFacilityType() == null) {
            bus.setFacilityType("AC");
        }

        return template;
    }

    /**
     * Generates physical seats for a trip schedule based on the assigned bus template.
     */
    @Transactional
    public List<Seat> generateTripSeats(BusSchedule schedule, Bus bus) {
        String templateId = bus != null ? bus.getTemplateId() : null;
        SeatLayoutTemplate template = null;
        if (templateId != null) {
            template = templateRepository.findById(templateId).orElse(null);
        }

        // Backward compatibility fallback for legacy buses
        if (template == null) {
            template = findFallbackTemplate(bus);
        }

        List<TemplatePosition> positions = positionRepository.findByTemplate_IdOrderByVisualOrderAsc(template.getId());
        double baseFare = schedule.getFare() != null ? schedule.getFare() : 500.0;

        List<Seat> seats = new ArrayList<>();
        for (TemplatePosition pos : positions) {
            double price = baseFare;
            // Higher tier for sleeper berths if applicable
            if (TYPE_SLEEPER.equalsIgnoreCase(pos.getPositionType())) {
                price = Math.round(baseFare * 1.25);
            }

            seats.add(Seat.builder()
                    .scheduleId(schedule.getId())
                    .seatNumber(pos.getSeatLabel())
                    .seatType(pos.getPositionType().equalsIgnoreCase(TYPE_SLEEPER) ? "Sleeper" : "Seater")
                    .price(price)
                    .status("AVAILABLE")
                    .deck(pos.getDeck())
                    .rowNumber(pos.getRowNumber())
                    .columnNumber(pos.getColumnNumber())
                    .side(pos.getSide())
                    .isBookable(pos.getIsBookable())
                    .rowSpan(pos.getRowSpan())
                    .colSpan(pos.getColSpan())
                    .visualOrder(pos.getVisualOrder())
                    .build());
        }

        List<Seat> savedSeats = seatRepository.saveAll(seats);
        schedule.setAvailableSeats(template.getTotalInventory());
        return savedSeats;
    }

    private SeatLayoutTemplate findFallbackTemplate(Bus bus) {
        int seats = bus != null && bus.getTotalSeats() != null ? bus.getTotalSeats() : 36;
        if (seats >= 48) {
            return templateRepository.findById(TEMPLATE_2_PLUS_2_SEATER)
                    .orElseGet(this::build2Plus2SeaterTemplate);
        } else if (seats <= 24) {
            return templateRepository.findById(TEMPLATE_1_PLUS_1_SEATER)
                    .orElseGet(this::build1Plus1SeaterTemplate);
        } else {
            return templateRepository.findById(TEMPLATE_2_PLUS_1_SEATER)
                    .orElseGet(this::build2Plus1SeaterTemplate);
        }
    }

    /**
     * Builds the structured trip seat layout response for the future traveller seat-selection UI.
     */
    @Transactional(readOnly = true)
    public TripSeatLayoutResponse getTripLayout(BusSchedule schedule, Bus bus) {
        String templateId = bus != null ? bus.getTemplateId() : null;
        SeatLayoutTemplate template = null;
        if (templateId != null) {
            template = templateRepository.findById(templateId).orElse(null);
        }
        if (template == null) {
            template = findFallbackTemplate(bus);
        }

        List<Seat> scheduleSeats = seatRepository.findByScheduleId(schedule.getId());
        Map<String, Seat> seatByNumber = scheduleSeats.stream()
                .collect(Collectors.toMap(Seat::getSeatNumber, s -> s, (s1, s2) -> s1));

        TripSeatLayoutResponse response = TripSeatLayoutResponse.builder()
                .scheduleId(schedule.getId())
                .busId(schedule.getBusId())
                .templateId(template.getId())
                .templateName(template.getDisplayName())
                .referenceImage(template.getReferenceImage())
                .busNumber(schedule.getBusNumber())
                .operatorName(schedule.getOperatorName())
                .busType(schedule.getBusType())
                .busConfiguration(bus != null && bus.getBusConfiguration() != null ? bus.getBusConfiguration() : template.getBusConfiguration())
                .facilityType(bus != null && bus.getFacilityType() != null ? bus.getFacilityType() : "AC")
                .amenities(bus != null ? bus.getAmenities() : "")
                .totalInventory(template.getTotalInventory())
                .availableSeats(schedule.getAvailableSeats())
                .baseFare(schedule.getFare())
                .origin(schedule.getOrigin())
                .destination(schedule.getDestination())
                .travelDate(schedule.getTravelDate() != null ? schedule.getTravelDate().toString() : null)
                .departureTime(schedule.getDepartureTime())
                .arrivalTime(schedule.getArrivalTime())
                .boardingPoints(schedule.getBoardingPoints())
                .droppingPoints(schedule.getDroppingPoints())
                .decks(new ArrayList<>())
                .build();

        // Organize positions into DeckLayoutDto
        List<TemplatePosition> allPositions = positionRepository.findByTemplate_IdOrderByVisualOrderAsc(template.getId());

        if (template.getDeckCount() == 1) {
            DeckLayoutDto singleDeck = buildDeckLayout(DECK_SINGLE, "Main Deck", template.getLowerRows(), template.getLowerColumns(), allPositions, seatByNumber, schedule.getFare());
            response.getDecks().add(singleDeck);
        } else {
            List<TemplatePosition> lowerPositions = allPositions.stream()
                    .filter(p -> DECK_LOWER.equalsIgnoreCase(p.getDeck()))
                    .collect(Collectors.toList());
            DeckLayoutDto lowerDeck = buildDeckLayout(DECK_LOWER, "Lower Deck", template.getLowerRows(), template.getLowerColumns(), lowerPositions, seatByNumber, schedule.getFare());
            response.getDecks().add(lowerDeck);

            List<TemplatePosition> upperPositions = allPositions.stream()
                    .filter(p -> DECK_UPPER.equalsIgnoreCase(p.getDeck()))
                    .collect(Collectors.toList());
            DeckLayoutDto upperDeck = buildDeckLayout(DECK_UPPER, "Upper Deck", template.getUpperRows(), template.getUpperColumns(), upperPositions, seatByNumber, schedule.getFare());
            response.getDecks().add(upperDeck);
        }

        return response;
    }

    private DeckLayoutDto buildDeckLayout(String deckCode, String deckName, Integer rows, Integer cols,
                                          List<TemplatePosition> positions, Map<String, Seat> seatByNumber,
                                          Double fallbackFare) {
        DeckLayoutDto deck = DeckLayoutDto.builder()
                .deck(deckCode)
                .deckName(deckName)
                .rows(rows != null ? rows : 12)
                .columns(cols != null ? cols : 4)
                .inventory(positions.size())
                .positions(new ArrayList<>())
                .build();

        int availCount = 0;
        for (TemplatePosition pos : positions) {
            Seat s = seatByNumber.get(pos.getSeatLabel());
            String status = s != null ? s.getStatus() : "AVAILABLE";
            Double price = s != null && s.getPrice() != null ? s.getPrice() : fallbackFare;

            if ("AVAILABLE".equalsIgnoreCase(status) && Boolean.TRUE.equals(pos.getIsBookable())) {
                availCount++;
            }

            deck.getPositions().add(SeatPositionDto.builder()
                    .positionId(pos.getPositionId())
                    .seatNumber(pos.getSeatLabel())
                    .seatType(pos.getPositionType())
                    .deck(pos.getDeck())
                    .row(pos.getRowNumber())
                    .column(pos.getColumnNumber())
                    .side(pos.getSide())
                    .isWindow(pos.getIsWindow())
                    .isBookable(pos.getIsBookable())
                    .rowSpan(pos.getRowSpan())
                    .colSpan(pos.getColSpan())
                    .price(price)
                    .status(status)
                    .gender("UNISEX")
                    .visualOrder(pos.getVisualOrder())
                    .build());
        }

        deck.setAvailableCount(availCount);
        return deck;
    }
}

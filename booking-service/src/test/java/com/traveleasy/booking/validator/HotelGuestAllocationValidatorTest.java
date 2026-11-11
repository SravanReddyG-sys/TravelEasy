package com.traveleasy.booking.validator;

import com.traveleasy.booking.dto.PrimaryGuestDto;
import com.traveleasy.booking.dto.ReservationAllocationItemDto;
import com.traveleasy.booking.dto.RoomGuestAllocationDto;
import com.traveleasy.booking.dto.RoomOccupantDto;
import com.traveleasy.booking.model.GuestGender;
import com.traveleasy.booking.model.GuestType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HotelGuestAllocationValidatorTest {

    private HotelGuestAllocationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HotelGuestAllocationValidator();
    }

    private RoomOccupantDto validAdult(String name) {
        return RoomOccupantDto.builder()
                .name(name)
                .type(GuestType.ADULT)
                .age(30)
                .gender(GuestGender.MALE)
                .build();
    }

    // ==========================================
    // 1. PRIMARY GUEST TESTS
    // ==========================================

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Valid primary guest accepted")
    void testValidPrimaryGuest_Accepted() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName("John Doe")
                .email("john.doe@example.com")
                .mobile("+919876543210")
                .build();

        assertDoesNotThrow(() -> validator.validatePrimaryGuest(primary));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Missing or null name rejected")
    void testMissingName_Rejected() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName(null)
                .email("john.doe@example.com")
                .mobile("+919876543210")
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validatePrimaryGuest(primary)
        );
        assertTrue(ex.getMessage().contains("Full name is mandatory"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Blank name rejected")
    void testBlankName_Rejected() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName("   ")
                .email("john.doe@example.com")
                .mobile("+919876543210")
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validatePrimaryGuest(primary)
        );
        assertTrue(ex.getMessage().contains("Full name is mandatory"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Invalid email rejected")
    void testInvalidEmail_Rejected() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName("John Doe")
                .email("not-an-email")
                .mobile("+919876543210")
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validatePrimaryGuest(primary)
        );
        assertTrue(ex.getMessage().contains("Email format is invalid"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Missing email rejected")
    void testMissingEmail_Rejected() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName("John Doe")
                .email("")
                .mobile("+919876543210")
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validatePrimaryGuest(primary)
        );
        assertTrue(ex.getMessage().contains("Email is mandatory"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Missing mobile rejected")
    void testMissingMobile_Rejected() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .mobile("  ")
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validatePrimaryGuest(primary)
        );
        assertTrue(ex.getMessage().contains("Mobile number is mandatory"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: Invalid short or non-numeric mobile rejected")
    void testInvalidMobile_Rejected() {
        PrimaryGuestDto primary = PrimaryGuestDto.builder()
                .fullName("John Doe")
                .email("john@example.com")
                .mobile("12345") // Only 5 digits
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validatePrimaryGuest(primary)
        );
        assertTrue(ex.getMessage().contains("Mobile number format is invalid"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Primary Guest: International phone formats accepted")
    void testInternationalMobile_Accepted() {
        List<String> validNumbers = List.of(
                "+91 98765 43210",
                "+1 (555) 123-4567",
                "09876543210",
                "+44 20 7946 0958"
        );

        for (String mobile : validNumbers) {
            PrimaryGuestDto primary = PrimaryGuestDto.builder()
                    .fullName("Alex Doe")
                    .email("alex@example.com")
                    .mobile(mobile)
                    .build();
            assertDoesNotThrow(() -> validator.validatePrimaryGuest(primary), "Should accept: " + mobile);
        }
    }

    // ==========================================
    // 2. OCCUPANCY TESTS
    // ==========================================

    @Test
    @DisplayName("WP-HB-06.1 Occupancy: Two adults accepted within capacity")
    void testTwoAdults_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(2)
                                .children(0)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        validAdult("Adult Two")
                                ))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateRoomAllocation(allocation, 3));
    }

    @Test
    @DisplayName("WP-HB-06.1 Occupancy: Two adults and one child accepted within capacity")
    void testTwoAdultsAndOneChild_WithinCapacity_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(2)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        validAdult("Adult Two"),
                                        RoomOccupantDto.builder().name("Child One").type(GuestType.CHILD).age(6).build()
                                ))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateRoomAllocation(allocation, 3));
    }

    @Test
    @DisplayName("WP-HB-06.1 Occupancy: Occupancy exceeding maxOccupancy rejected")
    void testOccupancyExceedingMaxOccupancy_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(2)
                                .children(2)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        validAdult("Adult Two"),
                                        RoomOccupantDto.builder().name("Child One").type(GuestType.CHILD).age(4).build(),
                                        RoomOccupantDto.builder().name("Child Two").type(GuestType.CHILD).age(8).build()
                                ))
                                .build()
                ))
                .build();

        // maxOccupancy is 3, total occupants is 4
        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 3)
        );
        assertTrue(ex.getMessage().contains("exceeds max occupancy limit (3)"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Occupancy: Zero total occupants rejected")
    void testZeroTotalOccupants_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(0)
                                .children(0)
                                .occupants(Collections.emptyList())
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 3)
        );
        assertTrue(ex.getMessage().contains("Total occupants (adults + children) must be at least 1"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Occupancy: Negative counts rejected")
    void testNegativeCounts_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(-1)
                                .children(0)
                                .occupants(Collections.emptyList())
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Adult count must be a non-negative integer"));
    }

    // ==========================================
    // 3. CHILDREN AGE TESTS
    // ==========================================

    @Test
    @DisplayName("WP-HB-06.1 Children: Age 0 accepted")
    void testChildAge0_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        RoomOccupantDto.builder().name("Infant Child").type(GuestType.CHILD).age(0).build()
                                ))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateRoomAllocation(allocation, 2));
    }

    @Test
    @DisplayName("WP-HB-06.1 Children: Age 17 accepted")
    void testChildAge17_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        RoomOccupantDto.builder().name("Teen Child").type(GuestType.CHILD).age(17).build()
                                ))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateRoomAllocation(allocation, 2));
    }

    @Test
    @DisplayName("WP-HB-06.1 Children: Age 18 rejected for child occupant")
    void testChildAge18_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        RoomOccupantDto.builder().name("Over-age Child").type(GuestType.CHILD).age(18).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("must be an integer between 0 and 17 inclusive"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Children: Missing child age rejected")
    void testMissingChildAge_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Adult One"),
                                        RoomOccupantDto.builder().name("No-Age Child").type(GuestType.CHILD).age(null).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Child age is mandatory for child occupants"));
    }

    @Test
    @DisplayName("WP-HB-UI-03 Adults: Missing adult age rejected")
    void testAdultWithOmittedAge_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(List.of(
                                        RoomOccupantDto.builder().name("Adult One").type(GuestType.ADULT).age(null).gender(GuestGender.MALE).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Adult age is mandatory for adult occupants"));
    }

    // ==========================================
    // 4. OCCUPANT CONSISTENCY TESTS
    // ==========================================

    @Test
    @DisplayName("WP-HB-06.1 Consistency: Correct occupant counts accepted")
    void testCorrectOccupantCounts_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(2)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Adult 1"),
                                        validAdult("Adult 2"),
                                        RoomOccupantDto.builder().name("Child 1").type(GuestType.CHILD).age(10).build()
                                ))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateRoomAllocation(allocation, 4));
    }

    @Test
    @DisplayName("WP-HB-06.1 Consistency: Adult count mismatch rejected")
    void testAdultCountMismatch_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(2) // Declared 2 adults
                                .children(0)
                                .occupants(List.of(
                                        validAdult("Adult 1"),
                                        RoomOccupantDto.builder().name("Child Mismatched").type(GuestType.CHILD).age(5).build() // Provided 1 adult, 1 child
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Declared adult count (2) does not match number of ADULT records (1)"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Consistency: Child count mismatch rejected")
    void testChildCountMismatch_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(2) // Declared 2 children
                                .occupants(List.of(
                                        validAdult("Adult 1"),
                                        RoomOccupantDto.builder().name("Child 1").type(GuestType.CHILD).age(5).build() // Only 1 child provided
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 4)
        );
        assertTrue(ex.getMessage().contains("Declared child count (2) does not match number of CHILD records (1)"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Consistency: Blank occupant name rejected")
    void testBlankOccupantName_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(List.of(
                                        RoomOccupantDto.builder().name("   ").type(GuestType.ADULT).age(25).gender(GuestGender.FEMALE).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Occupant name is mandatory and cannot be blank"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Consistency: Missing occupant list rejected when occupancy > 0")
    void testMissingOccupantList_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(Collections.emptyList())
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Occupants list is required when occupancy is 1"));
    }

    // ==========================================
    // 5. PHYSICAL-ROOM ALLOCATION TESTS
    // ==========================================

    @Test
    @DisplayName("WP-HB-06.1 Allocation: One allocation with one room accepted")
    void testOneAllocationWithOneRoom_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(201L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(List.of(validAdult("Solo Guest")))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateAllocations(List.of(allocation), Map.of(201L, 2)));
    }

    @Test
    @DisplayName("WP-HB-06.1 Allocation: One allocation with multiple rooms accepted")
    void testOneAllocationWithMultipleRooms_Accepted() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(201L)
                .quantity(2)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(2)
                                .children(0)
                                .occupants(List.of(
                                        validAdult("Guest A1"),
                                        validAdult("Guest A2")
                                ))
                                .build(),
                        RoomGuestAllocationDto.builder()
                                .roomSequence(2)
                                .adults(1)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Guest B1"),
                                        RoomOccupantDto.builder().name("Guest B2").type(GuestType.CHILD).age(7).build()
                                ))
                                .build()
                ))
                .build();

        assertDoesNotThrow(() -> validator.validateAllocations(List.of(allocation), Map.of(201L, 3)));
    }

    @Test
    @DisplayName("WP-HB-06.1 Allocation: Duplicate room sequence rejected")
    void testDuplicateRoomSequence_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(201L)
                .quantity(2)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(List.of(validAdult("Guest 1")))
                                .build(),
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1) // Duplicate sequence 1!
                                .adults(1)
                                .children(0)
                                .occupants(List.of(validAdult("Guest 2")))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 3)
        );
        assertTrue(ex.getMessage().contains("Duplicate room sequence 1 within allocation"));
    }

    @Test
    @DisplayName("WP-HB-06.1 Allocation: Quantity and room-list size mismatch rejected")
    void testQuantityAndRoomListSizeMismatch_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(201L)
                .quantity(2) // Declared 2 rooms, but only 1 entry provided
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(List.of(validAdult("Solo Guest")))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Number of physical-room entries (1) must equal allocation quantity (2)"));
    }

    // ==========================================
    // 6. WP-HB-UI-03 GENDER & ADULT AGE TESTS
    // ==========================================

    @Test
    @DisplayName("WP-HB-UI-03 Adults: All supported genders (MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY) accepted")
    void testAllSupportedGenders_Accepted() {
        for (GuestGender g : GuestGender.values()) {
            ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                    .roomId(101L)
                    .quantity(1)
                    .rooms(List.of(
                            RoomGuestAllocationDto.builder()
                                    .roomSequence(1)
                                    .adults(1)
                                    .children(0)
                                    .occupants(List.of(
                                            RoomOccupantDto.builder().name("Adult Guest").type(GuestType.ADULT).age(18).gender(g).build()
                                    ))
                                    .build()
                    ))
                    .build();
            assertDoesNotThrow(() -> validator.validateRoomAllocation(allocation, 2), "Gender should be accepted: " + g);
        }
    }

    @Test
    @DisplayName("WP-HB-UI-03 Adults: Adult missing gender rejected with structured detail")
    void testAdultMissingGender_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(0)
                                .occupants(List.of(
                                        RoomOccupantDto.builder().name("Rohit Sharma").type(GuestType.ADULT).age(32).gender(null).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 2)
        );
        assertTrue(ex.getMessage().contains("Adult gender is mandatory"));
        assertEquals(1, ex.getErrorDetails().size());
        assertEquals(1, ex.getErrorDetails().get(0).getRoomSequence());
        assertEquals(0, ex.getErrorDetails().get(0).getGuestIndex());
        assertTrue(ex.getErrorDetails().get(0).getField().endsWith(".gender"));
    }

    @Test
    @DisplayName("WP-HB-UI-03 Adults: Adult age below 18 (e.g. 17 or negative) rejected")
    void testAdultUnder18OrNegative_Rejected() {
        for (int badAge : List.of(17, 0, -5)) {
            ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                    .roomId(101L)
                    .quantity(1)
                    .rooms(List.of(
                            RoomGuestAllocationDto.builder()
                                    .roomSequence(1)
                                    .adults(1)
                                    .children(0)
                                    .occupants(List.of(
                                            RoomOccupantDto.builder().name("Young Guest").type(GuestType.ADULT).age(badAge).gender(GuestGender.OTHER).build()
                                    ))
                                    .build()
                    ))
                    .build();

            GuestAllocationValidationException ex = assertThrows(
                    GuestAllocationValidationException.class,
                    () -> validator.validateRoomAllocation(allocation, 2)
            );
            assertTrue(ex.getMessage().contains("must be at least 18"));
        }
    }

    @Test
    @DisplayName("WP-HB-UI-03 Children: Child with populated gender rejected")
    void testChildWithGenderPopulated_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(1)
                                .children(1)
                                .occupants(List.of(
                                        validAdult("Parent Guest"),
                                        RoomOccupantDto.builder().name("Aarav").type(GuestType.CHILD).age(8).gender(GuestGender.MALE).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 3)
        );
        assertTrue(ex.getMessage().contains("Gender must not be populated (must be null) for child occupants"));
    }

    @Test
    @DisplayName("WP-HB-UI-03 Room Rules: Room with only children and zero adults rejected")
    void testOnlyChildren_ZeroAdults_Rejected() {
        ReservationAllocationItemDto allocation = ReservationAllocationItemDto.builder()
                .roomId(101L)
                .quantity(1)
                .rooms(List.of(
                        RoomGuestAllocationDto.builder()
                                .roomSequence(1)
                                .adults(0)
                                .children(2)
                                .occupants(List.of(
                                        RoomOccupantDto.builder().name("Child 1").type(GuestType.CHILD).age(6).build(),
                                        RoomOccupantDto.builder().name("Child 2").type(GuestType.CHILD).age(9).build()
                                ))
                                .build()
                ))
                .build();

        GuestAllocationValidationException ex = assertThrows(
                GuestAllocationValidationException.class,
                () -> validator.validateRoomAllocation(allocation, 3)
        );
        assertTrue(ex.getMessage().contains("At least 1 adult is required per physical room"));
    }
}

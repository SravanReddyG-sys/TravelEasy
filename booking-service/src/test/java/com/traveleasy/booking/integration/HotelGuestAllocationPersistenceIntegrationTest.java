package com.traveleasy.booking.integration;

import com.traveleasy.booking.model.*;
import com.traveleasy.booking.repository.HotelAllocationSnapshotRepository;
import com.traveleasy.booking.repository.HotelReservationSnapshotRepository;
import com.traveleasy.booking.repository.HotelRoomGuestAllocationRepository;
import com.traveleasy.booking.repository.HotelRoomGuestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class HotelGuestAllocationPersistenceIntegrationTest {

    @Autowired
    private HotelReservationSnapshotRepository reservationSnapshotRepository;

    @Autowired
    private HotelAllocationSnapshotRepository allocationSnapshotRepository;

    @Autowired
    private HotelRoomGuestAllocationRepository roomGuestAllocationRepository;

    @Autowired
    private HotelRoomGuestRepository roomGuestRepository;

    private String testBookingRef;
    private Long testBookingId;

    @BeforeEach
    void setUp() {
        testBookingRef = "BKG-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        testBookingId = System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        reservationSnapshotRepository.findByBookingRef(testBookingRef)
                .ifPresent(r -> reservationSnapshotRepository.delete(r));
    }

    @Test
    @DisplayName("WP-HB-06.1 Persistence: Complete relational graph with primary guest, room allocations, and occupants")
    void testPersistence_ParentWithMultipleRoomsAndOccupants() {
        // 1. Create parent reservation snapshot with structured primary guest
        HotelReservationSnapshot snapshot = HotelReservationSnapshot.builder()
                .bookingId(testBookingId)
                .bookingRef(testBookingRef)
                .hotelId(42L)
                .hotelName("Grand Luxury Resort")
                .primaryGuestName("Rakesh Sharma")
                .primaryGuestEmail("rakesh.sharma@example.com")
                .primaryGuestMobile("+919876543210")
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .totalNights(3)
                .totalAmount(18000.0)
                .currency("INR")
                .status("HELD")
                .build();

        // 2. Create room-type allocation
        HotelAllocationSnapshot allocSnapshot = HotelAllocationSnapshot.builder()
                .roomId(205L)
                .roomTypeName("Deluxe Ocean Suite")
                .quantity(2)
                .allocationTotal(18000.0)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(5))
                .status("HELD")
                .build();

        // 3. Create physical-room allocations with occupants
        // Room Sequence 1: 2 Adults, 1 Child
        HotelRoomGuestAllocation room1 = HotelRoomGuestAllocation.builder()
                .roomSequence(1)
                .adultCount(2)
                .childCount(1)
                .build();

        HotelRoomGuest adult1 = HotelRoomGuest.builder()
                .guestName("Rakesh Sharma")
                .guestType(GuestType.ADULT)
                .age(35)
                .gender(com.traveleasy.booking.model.GuestGender.MALE)
                .build();
        HotelRoomGuest adult2 = HotelRoomGuest.builder()
                .guestName("Priya Sharma")
                .guestType(GuestType.ADULT)
                .age(32)
                .gender(com.traveleasy.booking.model.GuestGender.FEMALE)
                .build();
        HotelRoomGuest child1 = HotelRoomGuest.builder()
                .guestName("Aarav Sharma")
                .guestType(GuestType.CHILD)
                .age(6) // Child age stored, gender remains null
                .gender(null)
                .build();

        room1.addOccupant(adult1);
        room1.addOccupant(adult2);
        room1.addOccupant(child1);
        allocSnapshot.addRoomAllocation(room1);

        // Room Sequence 2: 1 Adult
        HotelRoomGuestAllocation room2 = HotelRoomGuestAllocation.builder()
                .roomSequence(2)
                .adultCount(1)
                .childCount(0)
                .build();

        HotelRoomGuest adult3 = HotelRoomGuest.builder()
                .guestName("Amit Sharma")
                .guestType(GuestType.ADULT)
                .age(28)
                .gender(com.traveleasy.booking.model.GuestGender.PREFER_NOT_TO_SAY)
                .build();

        room2.addOccupant(adult3);
        allocSnapshot.addRoomAllocation(room2);

        snapshot.addAllocation(allocSnapshot);

        // 4. Save entire graph via parent repository
        HotelReservationSnapshot savedSnapshot = reservationSnapshotRepository.save(snapshot);
        assertNotNull(savedSnapshot.getId());
        Long savedAllocId = savedSnapshot.getAllocations().get(0).getId();

        // 5. Retrieve and verify relational integrity
        HotelReservationSnapshot loaded = reservationSnapshotRepository.findByBookingRef(testBookingRef).orElseThrow();
        assertEquals("Rakesh Sharma", loaded.getPrimaryGuestName());
        assertEquals("rakesh.sharma@example.com", loaded.getPrimaryGuestEmail());
        assertEquals("+919876543210", loaded.getPrimaryGuestMobile());

        List<HotelRoomGuestAllocation> loadedRooms = roomGuestAllocationRepository
                .findByAllocationSnapshotIdOrderByRoomSequenceAsc(savedAllocId);
        assertEquals(2, loadedRooms.size());

        // Verify Room 1
        HotelRoomGuestAllocation loadedRoom1 = loadedRooms.get(0);
        assertEquals(1, loadedRoom1.getRoomSequence());
        assertEquals(2, loadedRoom1.getAdultCount());
        assertEquals(1, loadedRoom1.getChildCount());

        List<HotelRoomGuest> occupantsRoom1 = roomGuestRepository
                .findByRoomGuestAllocationIdOrderByIdAsc(loadedRoom1.getId());
        assertEquals(3, occupantsRoom1.size());
        assertEquals("Rakesh Sharma", occupantsRoom1.get(0).getGuestName());
        assertEquals(GuestType.ADULT, occupantsRoom1.get(0).getGuestType());
        assertEquals(35, occupantsRoom1.get(0).getAge());
        assertEquals(com.traveleasy.booking.model.GuestGender.MALE, occupantsRoom1.get(0).getGender());
        assertEquals(com.traveleasy.booking.model.GuestGender.FEMALE, occupantsRoom1.get(1).getGender());

        assertEquals("Aarav Sharma", occupantsRoom1.get(2).getGuestName());
        assertEquals(GuestType.CHILD, occupantsRoom1.get(2).getGuestType());
        assertEquals(6, occupantsRoom1.get(2).getAge());
        assertNull(occupantsRoom1.get(2).getGender());

        // Verify Room 2
        HotelRoomGuestAllocation loadedRoom2 = loadedRooms.get(1);
        assertEquals(2, loadedRoom2.getRoomSequence());
        assertEquals(1, loadedRoom2.getAdultCount());
        assertEquals(0, loadedRoom2.getChildCount());

        List<HotelRoomGuest> occupantsRoom2 = roomGuestRepository
                .findByRoomGuestAllocationIdOrderByIdAsc(loadedRoom2.getId());
        assertEquals(1, occupantsRoom2.size());
        assertEquals("Amit Sharma", occupantsRoom2.get(0).getGuestName());
        assertEquals(GuestType.ADULT, occupantsRoom2.get(0).getGuestType());
        assertEquals(28, occupantsRoom2.get(0).getAge());
        assertEquals(com.traveleasy.booking.model.GuestGender.PREFER_NOT_TO_SAY, occupantsRoom2.get(0).getGender());
    }

    @Test
    @DisplayName("WP-HB-06.1 Persistence: Unique room sequence constraint throws exception on duplicate sequence")
    void testPersistence_UniqueRoomSequenceConstraint_ThrowsException() {
        HotelReservationSnapshot snapshot = HotelReservationSnapshot.builder()
                .bookingId(testBookingId + 1)
                .bookingRef(testBookingRef + "-UNIQ")
                .hotelId(12L)
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .totalAmount(5000.0)
                .build();

        HotelAllocationSnapshot alloc = HotelAllocationSnapshot.builder()
                .roomId(301L)
                .quantity(2)
                .allocationTotal(5000.0)
                .build();

        // First room with sequence 1
        HotelRoomGuestAllocation room1 = HotelRoomGuestAllocation.builder()
                .roomSequence(1)
                .adultCount(1)
                .childCount(0)
                .build();
        alloc.addRoomAllocation(room1);

        // Duplicate room with sequence 1 under same allocationSnapshotId
        HotelRoomGuestAllocation duplicateRoom = HotelRoomGuestAllocation.builder()
                .roomSequence(1) // duplicate!
                .adultCount(1)
                .childCount(0)
                .build();
        alloc.addRoomAllocation(duplicateRoom);

        snapshot.addAllocation(alloc);

        assertThrows(DataIntegrityViolationException.class, () -> {
            reservationSnapshotRepository.saveAndFlush(snapshot);
        });
    }

    @Test
    @DisplayName("WP-HB-06.1 Backward Compatibility: Existing historical reservations without guest allocations remain readable")
    void testPersistence_BackwardCompatibility_HistoricalReservationsWithoutGuestsReadable() {
        // Historical reservation created without primary guest fields and without physical room allocations
        HotelReservationSnapshot historicalSnapshot = HotelReservationSnapshot.builder()
                .bookingId(testBookingId + 2)
                .bookingRef(testBookingRef + "-LEGACY")
                .hotelId(99L)
                .hotelName("Legacy Heritage Hotel")
                .primaryGuestName(null)
                .primaryGuestEmail(null)
                .primaryGuestMobile(null)
                .checkInDate(LocalDate.now().plusDays(10))
                .checkOutDate(LocalDate.now().plusDays(12))
                .totalNights(2)
                .totalAmount(8000.0)
                .currency("INR")
                .status("CONFIRMED")
                .build();

        HotelAllocationSnapshot alloc = HotelAllocationSnapshot.builder()
                .roomId(401L)
                .roomTypeName("Standard Heritage Room")
                .quantity(1)
                .allocationTotal(8000.0)
                .build();
        historicalSnapshot.addAllocation(alloc);

        HotelReservationSnapshot saved = reservationSnapshotRepository.save(historicalSnapshot);
        assertNotNull(saved.getId());

        // Read back from DB
        HotelReservationSnapshot loaded = reservationSnapshotRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Legacy Heritage Hotel", loaded.getHotelName());
        assertNull(loaded.getPrimaryGuestName());
        assertNull(loaded.getPrimaryGuestEmail());
        assertNull(loaded.getPrimaryGuestMobile());
        assertNotNull(loaded.getAllocations());
        assertEquals(1, loaded.getAllocations().size());

        // Room allocations list is empty but non-null
        List<HotelRoomGuestAllocation> rooms = roomGuestAllocationRepository
                .findByAllocationSnapshotIdOrderByRoomSequenceAsc(loaded.getAllocations().get(0).getId());
        assertTrue(rooms.isEmpty());

        // Cleanup
        reservationSnapshotRepository.delete(saved);
    }

    @Test
    @DisplayName("WP-HB-UI-03 Backward Compatibility: Historical adult guest records with null age and null gender remain readable")
    void testPersistence_BackwardCompatibility_HistoricalAdultNullAgeGenderReadable() {
        HotelReservationSnapshot legacySnapshot = HotelReservationSnapshot.builder()
                .bookingId(testBookingId + 3)
                .bookingRef(testBookingRef + "-LEGACY-ADULT")
                .hotelId(99L)
                .hotelName("Heritage Retreat")
                .primaryGuestName("Legacy Primary")
                .checkInDate(LocalDate.now().plusDays(15))
                .checkOutDate(LocalDate.now().plusDays(17))
                .totalNights(2)
                .totalAmount(6000.0)
                .currency("INR")
                .status("CONFIRMED")
                .build();

        HotelAllocationSnapshot alloc = HotelAllocationSnapshot.builder()
                .roomId(402L)
                .roomTypeName("Classic Room")
                .quantity(1)
                .allocationTotal(6000.0)
                .build();

        HotelRoomGuestAllocation room = HotelRoomGuestAllocation.builder()
                .roomSequence(1)
                .adultCount(1)
                .childCount(0)
                .build();
        room.addOccupant(HotelRoomGuest.builder()
                .guestName("Historical Adult")
                .guestType(GuestType.ADULT)
                .age(null)
                .gender(null)
                .build());
        alloc.addRoomAllocation(room);
        legacySnapshot.addAllocation(alloc);

        HotelReservationSnapshot saved = reservationSnapshotRepository.saveAndFlush(legacySnapshot);
        List<HotelRoomGuestAllocation> rooms = roomGuestAllocationRepository
                .findByAllocationSnapshotIdOrderByRoomSequenceAsc(saved.getAllocations().get(0).getId());
        assertEquals(1, rooms.size());
        List<HotelRoomGuest> guests = roomGuestRepository.findByRoomGuestAllocationIdOrderByIdAsc(rooms.get(0).getId());
        assertEquals(1, guests.size());
        assertEquals("Historical Adult", guests.get(0).getGuestName());
        assertNull(guests.get(0).getAge());
        assertNull(guests.get(0).getGender());

        reservationSnapshotRepository.delete(saved);
    }
}

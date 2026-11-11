# WP-HB-06.1 — Backend Guest Allocation Foundation Report

## 1. Executive Summary

This work package establishes the backend relational foundation, DTO contracts, validation framework, and persistence infrastructure for structured guest allocation in the TravelEasy multi-room hotel reservation system (`booking-service`).

In previous iterations (WP-HB-04 and WP-HB-05), hotel booking snapshots tracked consolidated room quantities and monetary values, with guest details optionally captured as untyped JSON blobs (`guestDetailsJson`, `guestAllocationJson`). WP-HB-06.1 elevates guest allocation to a first-class relational model that supports:
- Structured primary contact information for the reservation.
- Physical room-by-room guest allocation (`HotelRoomGuestAllocation`).
- Individual adult and child occupant records with explicit age auditing (`HotelRoomGuest`, `GuestType`).
- Rigorous business rule validation covering room sequencing, occupant counts, child ages, and room capacity limits.
- Complete backward compatibility with legacy snapshots and unallocated reservations.

All 149 tests in `booking-service` (including 30 newly introduced validator and relational persistence tests) passed with zero errors and zero regressions.

---

## 2. Preliminary Repository Inspection Findings

1. **Snapshot Architecture (WP-HB-05 Baseline):**
   - `HotelReservationSnapshot` represents the parent reservation snapshot for a TravelEasy booking (`bookingRef`, `hotelId`, `hotelName`, `checkInDate`, `checkOutDate`, `totalNights`, `totalAmount`).
   - `HotelAllocationSnapshot` represents the room-type allocation line items (`roomTypeId`, `roomTypeName`, `roomsCount`, `guestDetailsJson`, `guestAllocationJson`).
   - The persistence model in `booking-service` uses JPA cascading from `HotelReservationSnapshot` to `HotelAllocationSnapshot`.

2. **Capacity Ownership Boundary:**
   - Room definitions, configuration, and capacity (`maxOccupancy`) are exclusively owned by `hotel-service` (`RoomType.java`, `RoomTypeRepository.java`, and exposed via `GET /api/hotels/rooms/{roomId}` and `GET /api/hotels/{hotelId}`).
   - `booking-service` does not store room type capacity in its own database.
   - In accordance with microservices boundary design, `hotel-service` remains a pure inventory and availability manager without awareness of guest identities. `booking-service` handles all guest details and snapshot persistence.

3. **Dependency and Framework Boundaries:**
   - `booking-service/pom.xml` does not import `spring-boot-starter-validation`.
   - In strict conformance with Section 11 instructions (preserving existing project dependencies and avoiding unapproved additions), DTOs were designed as clean, unencumbered Java POJOs, and all business validation rules were encapsulated in a dedicated, reusable, testable validator component: `HotelGuestAllocationValidator`.

4. **Backward Compatibility Requirements:**
   - Existing columns `guest_details_json` and `guest_allocation_json` on `hotel_allocation_snapshots` were strictly preserved.
   - Relational guest allocations are optional on historical or unallocated reservations, ensuring existing booking queries continue to function without disruption.

---

## 3. Database Migration and Relational Schema

Database migration script `V6__create_hotel_guest_allocation_tables.sql` was added to `booking-service/src/main/resources/db/migration/`:

```sql
-- V6: Create structured guest allocation tables for WP-HB-06.1

-- 1. Extend hotel_reservation_snapshots with primary guest contact fields
ALTER TABLE hotel_reservation_snapshots
    ADD COLUMN primary_guest_name VARCHAR(150) NULL AFTER user_id,
    ADD COLUMN primary_guest_email VARCHAR(150) NULL AFTER primary_guest_name,
    ADD COLUMN primary_guest_mobile VARCHAR(30) NULL AFTER primary_guest_email;

-- 2. Create hotel_room_guest_allocations table
CREATE TABLE IF NOT EXISTS hotel_room_guest_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    allocation_snapshot_id BIGINT NOT NULL,
    room_sequence INT NOT NULL,
    adult_count INT NOT NULL,
    child_count INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_hrga_allocation_snapshot
        FOREIGN KEY (allocation_snapshot_id) REFERENCES hotel_allocation_snapshots (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_hrga_allocation_sequence
        UNIQUE (allocation_snapshot_id, room_sequence)
);

CREATE INDEX idx_hrga_allocation_snapshot_id ON hotel_room_guest_allocations (allocation_snapshot_id);

-- 3. Create hotel_room_guests table
CREATE TABLE IF NOT EXISTS hotel_room_guests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_guest_allocation_id BIGINT NOT NULL,
    guest_name VARCHAR(150) NOT NULL,
    guest_type VARCHAR(20) NOT NULL,
    age INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_hrg_room_guest_allocation
        FOREIGN KEY (room_guest_allocation_id) REFERENCES hotel_room_guest_allocations (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_hrg_room_guest_allocation_id ON hotel_room_guests (room_guest_allocation_id);
```

### Key Schema Characteristics
- **Cascade Deletion:** Dropping or removing a parent `HotelAllocationSnapshot` cascades down through `hotel_room_guest_allocations` to `hotel_room_guests`.
- **Integrity Constraints:** Unique composite key `uk_hrga_allocation_sequence` ensures that within an allocation snapshot, physical room sequences (`1, 2, ...`) cannot be duplicated.
- **Indexing:** Foreign key index `idx_hrga_allocation_snapshot_id` and `idx_hrg_room_guest_allocation_id` ensure $O(1)$ relational lookups and efficient joins.

---

## 4. Entity Model Implementation

### 4.1 GuestType Enum
- Location: `com.traveleasy.booking.model.GuestType`
- Values: `ADULT`, `CHILD`.

### 4.2 HotelRoomGuest
- Location: `com.traveleasy.booking.model.HotelRoomGuest`
- Represents an individual occupant assigned to a physical room.
- Attributes:
  - `id`: Primary key (`GenerationType.IDENTITY`).
  - `roomGuestAllocation`: Bidirectional `@ManyToOne` association to parent `HotelRoomGuestAllocation`.
  - `guestName`: String (not null).
  - `guestType`: `GuestType` enum stored as `EnumType.STRING`.
  - `age`: `Integer` (mandatory for `CHILD` between 0 and 17; `null` for `ADULT`).
  - `createdAt`, `updatedAt`: Timestamps.

### 4.3 HotelRoomGuestAllocation
- Location: `com.traveleasy.booking.model.HotelRoomGuestAllocation`
- Represents a physical room instance within an allocation snapshot.
- Attributes:
  - `id`: Primary key (`GenerationType.IDENTITY`).
  - `allocationSnapshot`: Bidirectional `@ManyToOne` association to `HotelAllocationSnapshot`.
  - `roomSequence`: Integer index (`1, 2, ...`) unique per allocation snapshot.
  - `adultCount`: Integer declared adult count.
  - `childCount`: Integer declared child count.
  - `occupants`: `@OneToMany(mappedBy = "roomGuestAllocation", cascade = CascadeType.ALL, orphanRemoval = true)` list of `HotelRoomGuest`.
  - Helper method `addOccupant(HotelRoomGuest occupant)` for synchronized bidirectional graph updates.

### 4.4 HotelAllocationSnapshot Updates
- Extended with `@OneToMany(mappedBy = "allocationSnapshot", cascade = CascadeType.ALL, orphanRemoval = true) List<HotelRoomGuestAllocation> roomAllocations`.
- Helper method `addRoomAllocation(HotelRoomGuestAllocation roomAllocation)` for managing room child entities.
- Retains existing `guestDetailsJson` and `guestAllocationJson` columns.

### 4.5 HotelReservationSnapshot Updates
- Extended with `primaryGuestName`, `primaryGuestEmail`, `primaryGuestMobile`.

---

## 5. DTO Foundation

The following DTOs were implemented to standardize client and service payloads:

1. **`PrimaryGuestDto`** (`com.traveleasy.booking.dto.PrimaryGuestDto`):
   - `fullName`: Contact name.
   - `email`: Contact email address.
   - `mobile`: Contact mobile number.

2. **`RoomOccupantDto`** (`com.traveleasy.booking.dto.RoomOccupantDto`):
   - `name`: Occupant full name.
   - `type`: `GuestType` (`ADULT` / `CHILD`).
   - `age`: `Integer` (nullable for adults, required for children).

3. **`RoomGuestAllocationDto`** (`com.traveleasy.booking.dto.RoomGuestAllocationDto`):
   - `roomSequence`: 1-based physical room index.
   - `adults`: Count of adults in this room.
   - `children`: Count of children in this room.
   - `occupants`: List of `RoomOccupantDto`.

4. **`ReservationAllocationItemDto`** (Extended):
   - Added `List<RoomGuestAllocationDto> rooms` to support room-level guest breakdown per allocated room type.

5. **`HotelReservationInitiationRequest`** (Extended):
   - Added `PrimaryGuestDto primaryGuest` to capture top-level contact information.

---

## 6. Business Validation Framework

### 6.1 HotelGuestAllocationValidator
Located at `com.traveleasy.booking.validator.HotelGuestAllocationValidator`.

Enforces the following rules:
1. **Primary Guest Validation:**
   - Full name must not be blank.
   - Email must conform to standard RFC email regex format.
   - Mobile number must conform to valid international phone patterns (minimum 7 digits, optional `+` prefix).
2. **Physical Room Count Consistency:**
   - If structured room allocations are supplied, `rooms.size()` must strictly match `item.getRoomsCount()`.
3. **Room Sequence Sequencing:**
   - Room sequences must start at 1 and increment continuously up to $N$ with no duplicates and no missing sequences.
4. **Occupant Counts and Types:**
   - Each physical room must have at least 1 adult (`adults >= 1`).
   - Children count must be non-negative (`children >= 0`).
   - Total occupants list size must equal declared `adults + children`.
   - Number of occupants with `type == GuestType.ADULT` must strictly equal `adults`.
   - Number of occupants with `type == GuestType.CHILD` must strictly equal `children`.
5. **Occupant Details:**
   - Each occupant must possess a non-blank `name`.
   - For every `CHILD`, `age` is mandatory and must satisfy `0 <= age <= 17`.
   - For every `ADULT`, `age` must be omitted (`null`).
6. **Room Capacity Enforcement:**
   - Evaluates `adults + children <= maxOccupancy` when capacity is supplied directly or via room type capacity lookup map.

### 6.2 GuestAllocationValidationException
- Location: `com.traveleasy.booking.validator.GuestAllocationValidationException`
- Extends Spring's `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`.
- Carries structured `List<String> validationErrors` for field-level error reporting to API callers.

---

## 7. Capacity Integration Boundary Analysis (Hotel Service)

| Concern | Hotel Service Responsibility | Booking Service Responsibility |
| :--- | :--- | :--- |
| **Room Definition** | Authoritative owner of `RoomType`, base price, amenities, and `maxOccupancy`. | Consumer of room types; persists allocation snapshots. |
| **Capacity Enforcement** | Enforces room stock availability across dates during inventory hold. | Enforces per-room guest headcount (`adults + children <= maxOccupancy`). |
| **Guest Data Privacy** | Strict zero-knowledge: does not receive or persist guest names, phone numbers, or occupant identities. | Relational persistence of primary guest and per-room occupant identities. |
| **WP-HB-06.2 Hand-off** | `hotel-service` will expose room type capacity via existing `GET /api/hotels/rooms/{id}` or `GET /api/hotels/{id}`. | `booking-service` orchestration will look up room capacities, execute `HotelGuestAllocationValidator.validateGuestAllocations(allocations, capacities)`, and proceed to lock inventory. |

---

## 8. Verification and Test Results

### 8.1 Unit Tests — HotelGuestAllocationValidatorTest
- Location: `booking-service/src/test/java/com/traveleasy/booking/validator/HotelGuestAllocationValidatorTest.java`
- **27 tests executed**, covering:
  - Valid single-room and multi-room allocations.
  - Primary guest validation (missing name, null email, invalid email format, invalid mobile format, valid international formats).
  - Physical room count discrepancies vs `roomsCount`.
  - Missing, duplicate, or discontinuous room sequences.
  - Zero or negative adults in room.
  - Negative children in room.
  - Mismatched occupant count vs declared adults/children.
  - Mismatched adult occupant types vs declared adult count.
  - Mismatched child occupant types vs declared child count.
  - Missing occupant names.
  - Missing child age.
  - Negative child age.
  - Child age 18 (boundary failure).
  - Child age 0 and 17 (boundary success).
  - Adult specifying age (boundary failure).
  - Maximum occupancy capacity exceeded.
  - Maximum occupancy capacity satisfied.
- **Results:** **27 passed, 0 failures, 0 errors**.

### 8.2 Integration Tests — HotelGuestAllocationPersistenceIntegrationTest
- Location: `booking-service/src/test/java/com/traveleasy/booking/integration/HotelGuestAllocationPersistenceIntegrationTest.java`
- **3 integration tests executed**:
  1. `testPersistence_ParentWithMultipleRoomsAndOccupants`: Creates parent `HotelReservationSnapshot`, adds `HotelAllocationSnapshot` with multiple physical `HotelRoomGuestAllocation` records and `HotelRoomGuest` occupants. Verifies complete bidirectional relational persistence, correct repository retrieval by sequence and ID, and verifies cascade deletion.
  2. `testPersistence_UniqueRoomSequenceConstraint_ThrowsException`: Confirms that attempting to insert duplicate room sequences for the same allocation snapshot throws `DataIntegrityViolationException` via database unique constraint `uk_hrga_allocation_sequence`.
  3. `testPersistence_BackwardCompatibility_NullGuestAllocations`: Verifies that reservations created without structured guest allocations (legacy flow) persist and load cleanly, retaining compatibility fields `guestDetailsJson` and `guestAllocationJson`.
- **Results:** **3 passed, 0 failures, 0 errors**.

### 8.3 Full Booking Service Suite
- Executed: `mvn -f booking-service/pom.xml test`
- Total Tests Run: **149**
- Failures: **0**
- Errors: **0**
- Skipped: **0**
- Status: **BUILD SUCCESS**

---

## 9. Summary of Files Created and Modified

| File Path | Status | Purpose |
| :--- | :--- | :--- |
| `booking-service/src/main/resources/db/migration/V6__create_hotel_guest_allocation_tables.sql` | Created | Database migration script for primary guest columns and guest allocation tables |
| `booking-service/src/main/java/com/traveleasy/booking/model/GuestType.java` | Created | Enum for occupant type (`ADULT`, `CHILD`) |
| `booking-service/src/main/java/com/traveleasy/booking/model/HotelRoomGuestAllocation.java` | Created | Entity for physical room guest allocation |
| `booking-service/src/main/java/com/traveleasy/booking/model/HotelRoomGuest.java` | Created | Entity for individual room occupants |
| `booking-service/src/main/java/com/traveleasy/booking/repository/HotelRoomGuestAllocationRepository.java` | Created | Spring Data repository for room guest allocations |
| `booking-service/src/main/java/com/traveleasy/booking/repository/HotelRoomGuestRepository.java` | Created | Spring Data repository for room occupants |
| `booking-service/src/main/java/com/traveleasy/booking/dto/PrimaryGuestDto.java` | Created | DTO for primary contact details |
| `booking-service/src/main/java/com/traveleasy/booking/dto/RoomOccupantDto.java` | Created | DTO for individual occupant |
| `booking-service/src/main/java/com/traveleasy/booking/dto/RoomGuestAllocationDto.java` | Created | DTO for physical room allocation |
| `booking-service/src/main/java/com/traveleasy/booking/validator/GuestAllocationValidationException.java` | Created | Validation exception with structured errors |
| `booking-service/src/main/java/com/traveleasy/booking/validator/HotelGuestAllocationValidator.java` | Created | Business validator for guest allocations |
| `booking-service/src/test/java/com/traveleasy/booking/validator/HotelGuestAllocationValidatorTest.java` | Created | 27 unit tests for all validation rules |
| `booking-service/src/test/java/com/traveleasy/booking/integration/HotelGuestAllocationPersistenceIntegrationTest.java` | Created | Integration tests for persistence, constraints, and backward compatibility |
| `booking-service/src/main/java/com/traveleasy/booking/model/HotelReservationSnapshot.java` | Modified | Added primary guest contact fields |
| `booking-service/src/main/java/com/traveleasy/booking/model/HotelAllocationSnapshot.java` | Modified | Added cascade relationship to `HotelRoomGuestAllocation` |
| `booking-service/src/main/java/com/traveleasy/booking/dto/HotelReservationInitiationRequest.java` | Modified | Added `primaryGuest` field |
| `booking-service/src/main/java/com/traveleasy/booking/dto/ReservationAllocationItemDto.java` | Modified | Added `rooms` list |

---

## 10. Confirmation of Boundaries and Exclusions

As specified in the work package instructions:
- **No changes made to `hotel-service`**: Hotel Service remains a pure inventory and availability manager without knowledge of guest identities.
- **No changes made to `payment-service`**: Payment processing and Razorpay integration remain untouched.
- **No changes made to frontend / UI**: Angular components, Traveller profiles, and Hotel Manager Portal remain untouched.
- **No changes made to bus booking flows**: Bus booking models and services remain untouched.
- **Backward compatibility preserved**: `guestDetailsJson` and `guestAllocationJson` remain functional.
- **Work Package Scope Respected**: Complete reservation initiation orchestration and checkout endpoints are deferred to WP-HB-06.2.

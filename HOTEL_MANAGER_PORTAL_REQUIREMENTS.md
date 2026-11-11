# TravelEasy – Hotel Manager Portal
# Business Requirements & Engineering Decisions

**Document Type:** Living Requirements Specification  
**Project:** TravelEasy  
**Scope:** Existing Hotel Manager Portal and its interlinked microservices  
**Status:** Initial Business Baseline – Approved Decisions  
**Version:** 1.0

---

## 1. Project Context

TravelEasy is an existing microservices-based travel booking application.

The application already contains a dedicated `hotel-service` microservice and an existing Hotel Manager Portal in the Angular frontend.

The objective is to enhance the existing implementation rather than build a replacement.

Enhancements must cover:

- UI/UX improvements.
- Existing functionality refinement.
- Business logic corrections.
- Missing functionality implementation.
- Backend architecture improvements where justified.
- Integration with existing TravelEasy microservices.

### 1.1 Existing Technology Stack

- Frontend: Angular 20, Bootstrap.
- Backend: Java 17, Spring Boot.
- Database: MySQL.
- Architecture: Microservices.
- API communication: REST through API Gateway.

### 1.2 Relevant Microservices

- `auth-service`
- `hotel-service`
- `booking-service`
- `payment-service`
- `notification-service`
- `review-service`
- API Gateway.

The existing application and its established architecture must remain the foundation of all enhancements.

---

## 2. Project Objectives

1. Transform the existing Hotel Manager Portal into a comprehensive hotel operations management platform.
2. Improve existing hotel and room management functionality.
3. Establish reliable date-wise inventory and availability management.
4. Improve booking lifecycle and cancellation integration.
5. Introduce operational capabilities for hotel managers.
6. Ensure proper security and resource ownership enforcement.
7. Improve the portal UI/UX while maintaining consistency with other TravelEasy dashboards.

---

## 3. Approved Business Decisions

The following decisions have been discussed and approved as the initial business baseline.

### BD-01: Date-wise Room Inventory

**Decision:** Hotel inventory must be maintained on a date-wise basis.

Requirements:

- Inventory must be associated with a room type and individual calendar dates.
- Availability must be calculated for the requested stay.
- Each occupied night must be considered separately.
- Check-in date is inclusive; checkout date is exclusive.
- Concurrent reservations must not cause overbooking.
- Inventory must be restored when reservations are validly cancelled or expired.
- Inventory records must preserve allocation history where required.

### BD-02: Multiple Hotel Properties per Manager

**Decision:** A single hotel-manager account can manage multiple hotel properties.

Requirements:

- One manager can own multiple properties.
- Managers must have a unified view of their properties.
- Property-specific management must remain available.
- Bookings, inventory, pricing and operational information must be associated with the correct property.
- Managers must not access another manager's properties.

### BD-03: Immediate Hotel Property Activation

**Decision:** Newly registered properties should become active immediately without separate administrator approval.

Requirements:

- An approved hotel-manager account can register properties.
- Newly registered properties should receive `ACTIVE` status.
- Separate property-level administrator approval is not required.
- Existing manager account verification must remain intact.
- Administrators must retain the ability to suspend or deactivate properties.
- Inactive properties must not accept new reservations.

Important distinction: Manager account verification and hotel property activation are separate processes.

### BD-04: Platform-wide Cancellation Policy

**Decision:** TravelEasy must implement one centrally managed cancellation policy applicable across hotel properties.

Requirements:

- Cancellation rules must be centrally configurable.
- Individual hotels cannot independently override the platform policy.
- Cancellation eligibility and refund calculation must be handled by the appropriate booking-domain functionality.
- Payment execution must remain the responsibility of `payment-service`.
- Hotel inventory must be restored following valid cancellation.
- The applicable policy at booking time must be preserved with the reservation.
- Actual cancellation windows, refund percentages, fees and no-show rules require detailed specification.

### BD-05: Traveller Hotel Details and Room Discovery

**Decision:** Room types, prices and availability should be displayed on the hotel details page rather than the initial hotel search results.

Requirements:

- Hotel search primarily discovers properties.
- Hotel details display property information and available room types.
- Room availability must use the selected check-in and checkout dates.
- Prices must reflect the selected stay.
- Room capacity and guest requirements must be validated.
- Availability must be revalidated before inventory is reserved.

### BD-06: Automatic Date-wise Inventory Allocation

**Decision:** Hotel managers configure the total physical room count per room type. TravelEasy automatically manages date-wise availability.

Requirements:

- Managers configure total inventory for each room type.
- Daily inventory is derived from total inventory, bookings, active holds and operational blocks.
- Managers should not normally configure availability separately for every date.
- Every occupied night must have sufficient inventory.
- Checkout date must not consume another night's inventory.
- Inventory reservation and release operations must be atomic.
- Total allocations must never exceed physical inventory.

Illustrative inventory model:

`Available = Total Inventory - Booked Inventory - Active Holds - Blocked Inventory`

The final implementation must account for the actual lifecycle and expiry state of each allocation.

## BD-07: Room Type Modification, Structural Restrictions & Archival

**Status:** Approved — Finalized  
**Applies to:** Hotel Domain (WP-02), Inventory (WP-03), Booking Lifecycle (WP-04)

### 1. General Principle

Hotel managers can modify room-type information while preserving the integrity of existing reservations and historical booking records.

Room-type fields are classified into:

- Non-structural fields: Changes do not invalidate existing reservations and are generally permitted.
- Structural fields: Changes can affect room capacity, guest eligibility or inventory commitments and are subject to restrictions.
- System-managed fields: Values are maintained by the application and cannot be directly modified by hotel managers.
- Immutable fields: Identifiers and ownership relationships that cannot be changed through ordinary updates.

### 2. Room-Type Field Classification

| Field | Classification | Modification Rule |
|---|---|---|
| Room Type Name | Non-structural | Editable even when active bookings exist. Duplicate active room-type names within the same property are prohibited. |
| Description | Non-structural, if supported | Editable even when active bookings exist. |
| Amenities | Non-structural | Editable even when active bookings exist. |
| Price per Night | Non-structural | Editable at any time, including when confirmed bookings exist. New prices apply only to new bookings. Existing bookings retain their originally agreed prices. |
| Maximum Occupancy | Structural | Both increases and decreases are restricted while active bookings exist for the affected room type. |
| Total Rooms | Structural | Increases are permitted. Reductions are permitted only when existing reservations and other committed allocations are not violated. |
| Available Rooms | System-managed | Cannot be directly edited by hotel managers. Maintained by the inventory system. |
| Room Type Status | Lifecycle-managed | System controls `ACTIVE` and `ARCHIVED` status transitions through authorized operations. |
| Room Type ID | Immutable | Cannot be modified or regenerated during updates. |
| Hotel ID | Immutable ownership relationship | A room type cannot be reassigned to another property through an update operation. |

The classification applies only to fields that exist in the actual room-type domain model. Additional fields must be classified according to their business impact before being made editable.

### 3. Non-Structural Modification Rules

The following changes are permitted even when active bookings exist:

- Room type name changes.
- Description changes, where supported.
- Amenity changes.
- Price-per-night changes.

These modifications must not alter the terms of already confirmed bookings.

**Price preservation rule:**

When a manager changes the room-type price:

1. The new price applies to bookings created after the price update.
2. Existing confirmed bookings retain the price agreed at booking time.
3. Historical booking records must not dynamically retrieve and substitute the latest room-type price.
4. Booking records must preserve the relevant agreed pricing information.

Room type identifiers must remain unchanged so that historical references remain valid.

### 4. Structural Modification Rules

#### 4.1 Maximum Occupancy

Both increases and decreases to maximum occupancy are restricted while active bookings exist for the affected room type.

Once no affected active bookings remain, the manager may modify maximum occupancy, subject to normal validation.

The exact booking statuses considered active must be aligned with the booking lifecycle implemented in `booking-service`.

#### 4.2 Total Room Capacity

Managers may increase the total number of rooms.

A reduction is permitted only if the resulting capacity does not violate existing reservations or other committed allocations.

The inventory implementation must perform this validation against the applicable allocations, including date-wise allocations introduced in WP-03.

A reduction that would invalidate existing commitments must be rejected with a meaningful validation error.

### 5. System-Managed Fields

`availableRooms` must never be directly editable by hotel managers.

It is maintained by the inventory management functionality.

WP-03 will introduce date-wise inventory management and the corresponding availability calculations. The current global inventory counter must not be treated as the final inventory architecture.

Room type identifiers and property ownership relationships must also remain immutable through normal update operations.

### 6. Room-Type Archival

Permanent deletion of room types is prohibited.

- A room type with active allocations or reservations cannot be archived.
- A room type with no active allocations may be archived.
- Archival changes the room type status to `ARCHIVED`.
- Archived records must remain in the database to preserve historical references.
- Archived room types must not appear in public hotel discovery or accept new reservations.
- Authorized managers may view archived room types in their management interface.
- Historical bookings associated with archived room types must remain accessible.

### 7. Historical Booking Integrity

Changes to room-type information must not rewrite historical booking details.

At minimum, existing bookings must preserve:

- Hotel/property reference.
- Room-type reference.
- Room-type name as agreed at booking time.
- Agreed price per night.
- Applicable guest capacity at booking time.

The booking domain must own the historical booking snapshot and preserve its integrity independently of subsequent room-type modifications.

If any required snapshot fields are missing from the existing booking model, address their implementation in WP-04.

### 8. Work Package Responsibilities

| Work Package | Responsibility |
|---|---|
| WP-02 | Room-type field validation, modification classification, structural restrictions, ownership and archival rules. |
| WP-03 | Date-wise inventory, capacity allocation, availability calculations and validation of inventory reductions against committed allocations. |
| WP-04 | Booking lifecycle status definitions, historical booking snapshots and preservation of agreed booking prices. |

### 9. Acceptance Criteria

- Non-structural fields can be updated without invalidating existing bookings.
- Price changes affect only new bookings.
- Existing confirmed bookings retain their agreed prices.
- Both increases and decreases in maximum occupancy are rejected while affected active bookings exist.
- Total room capacity cannot be reduced below committed allocations.
- Available room counts cannot be directly edited by managers.
- Room-type identifiers and property ownership cannot be changed through ordinary updates.
- Duplicate active room-type names within the same property are rejected.
- Room types with active allocations cannot be archived.
- Archived room types remain available for historical references but are excluded from public discovery and new reservations.
- All changes preserve existing booking references and ownership boundaries.

### BD-08: Soft Property Deactivation

**Decision:** Properties must support soft deactivation.

Requirements:

- Deactivation changes property status to `INACTIVE`.
- Inactive properties must not appear as bookable properties.
- New bookings and inventory holds must be rejected.
- Existing reservations must be preserved.
- Deactivation must not automatically cancel reservations or initiate refunds.
- Existing reservations must remain manageable.
- Authorized managers and administrators must be able to reactivate properties subject to validation.
- Historical property and booking records must remain available.

### BD-09: Hotel Reservation Management

**Decision:** Hotel managers must be able to manage operational aspects of customer reservations.

Supported operations:

- View reservations.
- Confirm reservations where the established booking workflow explicitly requires manual confirmation.
- Check in guests.
- Check out guests.
- Mark eligible reservations as no-show.

Requirements:

- Successful payment and booking confirmation must be respected.
- Managers cannot manually confirm unpaid or payment-pending reservations.
- Check-in must follow booking-date and reservation-status validation.
- Check-out requires an eligible checked-in reservation.
- No-show must follow defined arrival and cancellation conditions.
- Every status transition must be validated by the backend.
- Booking status history must be preserved.
- Managers can only manage reservations associated with their properties.
- Booking lifecycle ownership must remain with `booking-service`.

### BD-10: Operational Room Availability Blocking

**Decision:** Hotel managers can block room inventory for maintenance and other operational reasons.

Requirements:

- Managers can block a quantity of rooms for a selected room type.
- Blocking must support date ranges.
- A reason is mandatory.
- Blocks must reduce availability for every affected night.
- Existing bookings and other blocks must be respected.
- Blocking must be rejected if it exceeds available inventory.
- Managers can view active and historical blocks.
- Authorized managers can release or modify blocks subject to validation.
- All block and release operations must be auditable.

The initial implementation uses quantity-based room-type inventory blocking, not individual physical room-number management.

---

## 4. Proposed Microservice Responsibility Boundaries

These are the intended domain ownership boundaries. The implementation agent must verify them against the existing architecture before making changes.

| Responsibility | Intended Service |
|---|---|
| Hotel property management | `hotel-service` |
| Room type management | `hotel-service` |
| Date-wise room inventory | `hotel-service` |
| Operational room blocking | `hotel-service` |
| Hotel room pricing | `hotel-service` |
| Booking lifecycle | `booking-service` |
| Cancellation policy evaluation | Centrally managed booking functionality |
| Payment and refunds | `payment-service` |
| Authentication and manager identity | `auth-service` |
| Notifications | `notification-service` |
| Reviews and ratings | `review-service` |

### Integration Requirements

- Inventory reservation and booking creation must be reliably coordinated.
- Failed payments and expired holds must release inventory.
- Valid cancellations must restore inventory.
- Hotel-manager booking actions must integrate with booking-service.
- Refund operations must integrate with payment-service.
- Hotel managers should receive appropriate booking and cancellation notifications.
- Review information must be retrieved through the established review-service integration.

Do not introduce duplicate ownership of business data across services.

---

## 5. Security and Authorization Requirements

1. Hotel Manager Portal routes must be protected.
2. Backend endpoints must validate JWT authentication.
3. Hotel-manager role authorization must be enforced.
4. Property ownership must be verified for every protected operation.
5. Room operations must verify ownership through the associated property.
6. Booking operations must verify that the reservation belongs to a property managed by the authenticated manager.
7. Administrators must retain appropriate privileged access.
8. Direct microservice access must not bypass security requirements.
9. Frontend visibility restrictions must never be treated as sufficient authorization.

---

## 6. Additional Business Rules Requiring Detailed Specification

The following topics have not yet received final detailed rules.

| ID | Topic | Initial Direction |
|---|---|---|
| TBD-01 | Inventory reduction | Reject reductions that violate existing allocations |
| TBD-02 | No-show | Define arrival deadline and refund treatment |
| TBD-03 | Cancellation | Define cancellation windows, refund percentages and fees |
| TBD-04 | Structural room edits | Establish exact field-level restrictions |
| TBD-05 | Manual booking confirmation | Verify whether any existing workflow requires it |
| TBD-06 | Inventory holds | Define duration, expiry and release behavior |
| TBD-07 | Property deactivation | Preserve and honor existing reservations unless explicitly intervened upon by an authorized administrator |

These requirements must be resolved during the relevant work package specifications.

---

## 7. Implementation Principles

All development agents must follow these principles:

1. Work with the existing TravelEasy application.
2. Do not rebuild existing modules unnecessarily.
3. Do not introduce duplicate APIs, entities or business logic.
4. Do not introduce unnecessary microservices.
5. Preserve existing working integrations unless an approved change requires modification.
6. Inspect existing implementation patterns before proposing architectural changes.
7. Maintain database and API consistency.
8. Implement appropriate validation and transaction management.
9. Add automated tests for new and modified business logic.
10. Maintain backward compatibility wherever practical.
11. Do not silently change approved business decisions.
12. Identify conflicts between existing implementation and approved requirements before making changes.

---

## 8. Work Package Reference

The project is organized into the following proposed work packages:

| WP | Scope | Status |
|---|---|---|
| WP-01 | Security and Authorization | Planned |
| WP-02 | Hotel Domain and Business Logic | Planned |
| WP-03 | Inventory, Pricing and Availability | Planned |
| WP-04 | Booking Lifecycle and Integration | Planned |
| WP-05 | Hotel Manager Portal UI and Functionality | Planned |
| WP-06 | Testing and Stabilization | Planned |

Each work package must have its own detailed specification, implementation instructions and review report.

---

## 9. Change Management

This document is the central reference for approved hotel-domain business decisions.

- Approved decisions must not be changed without explicit authorization.
- New requirements must receive unique identifiers.
- Changes must include rationale and affected modules.
- Superseded decisions must remain traceable in the revision history.
- Unresolved decisions must remain explicitly marked as pending.
- The document should be updated whenever a business decision is finalized or revised.

---

## 10. Revision History

| Version | Description | Status |
|---|---|---|
| 1.0 | Initial ten business decisions and engineering boundaries documented | Initial baseline |

**End of Document**
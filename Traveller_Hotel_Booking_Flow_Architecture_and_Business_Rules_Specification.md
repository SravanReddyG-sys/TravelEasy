# TravelEasy – Traveller Hotel Booking Flow
## Architecture & Business Rules Specification

**Version:** 1.0  
**Date:** 9 November 2026  
**Status:** Proposed – Architecture and Business Rules Baseline

---

# 1. Objective

The objective is to redesign and extend the existing Traveller Hotel Booking Flow to support a realistic, date-aware, multi-room hotel reservation experience.

The existing implementation already supports date-wise room inventory, date-specific pricing, room availability checks, reservation holds, booking lifecycle management, payment integration and cancellation.

However, the existing reservation allocation model identifies a single room allocation using a unique booking reference. This prevents multiple room types from being reserved under one booking.

The updated architecture must support:

- Multiple room types within one hotel reservation.
- Multiple rooms of the same room type.
- Date-wise availability and pricing.
- Individual guest allocation for every selected room.
- Individual children's age capture.
- One primary guest and contact information per reservation.
- One booking reference and consolidated payment.
- Atomic reservation of all selected room allocations.
- Consistent booking, payment, inventory and cancellation lifecycles.

The implementation must reuse existing services and integrations wherever possible rather than introducing duplicate infrastructure.

# 2. Confirmed Business Rules

These decisions are the agreed baseline for implementation.

## 2.1 Reservation structure

1. A traveller can select multiple room types from the same hotel.
2. A traveller can select multiple rooms of the same room type.
3. All selected rooms belong to one hotel reservation.
4. The reservation receives one unique booking reference.
5. All room allocations are associated with that booking reference.
6. The reservation uses one consolidated payment transaction.

Example:

| Room type | Quantity |
|---|---:|
| Deluxe Room | 2 |
| Family Suite | 1 |
| Premium Room | 1 |

The reservation contains four rooms across three room types, but has only one booking reference.

## 2.2 Guest allocation

Guest allocation must be configured separately for every selected room.

Each room must capture:

- Number of adults.
- Number of children.
- Individual age of every child.
- Names of occupants assigned to the room.

Example:

| Room | Adults | Children | Children's ages |
|---|---:|---:|---|
| Deluxe Room 1 | 2 | 0 | — |
| Deluxe Room 2 | 2 | 2 | 5, 9 |
| Family Suite 1 | 3 | 1 | 7 |

Each room's occupancy must be validated against its room type's occupancy restrictions.

## 2.3 Primary guest information

Primary guest information is collected once per reservation.

Required information:

- Full name.
- Email address.
- Mobile number.

The primary guest acts as the main contact for the entire reservation.

Individual occupant names are collected separately for each room.

## 2.4 Children's age

Every child must have an individually specified age.

The frontend must dynamically generate one age input for each child.

When the child count changes:

- Additional age inputs must be generated.
- Removed children must have their corresponding age values removed.
- All ages must be validated before reservation submission.

The backend must validate the received information rather than relying exclusively on frontend validation.

## 2.5 Cancellation

Cancellation is initially supported only at the whole-booking level.

- All rooms under one reservation are cancelled together.
- All associated hotel inventory allocations are released.
- The existing platform-wide cancellation policy is retained.
- Partial cancellation of individual rooms is excluded from the initial implementation.

Existing cancellation policy:

| Cancellation timing | Refund |
|---|---:|
| 48 hours or more before check-in | 100% |
| 24–48 hours before check-in | 50% |
| Less than 24 hours before check-in | 0% |
| After 14:00 on check-in day | 0% |

The cancellation policy snapshot stored with the booking must remain authoritative for that booking.

# 3. Existing Architecture and Capabilities

The following functionality was identified during the read-only repository audit.

## 3.1 Hotel Service

The existing hotel service already manages:

- Hotels and room types.
- Date-wise room inventory.
- Date-specific pricing.
- Room availability.
- Stay availability calculations.
- Reservation holds.
- Reservation confirmation.
- Reservation release.
- Room-type activation and property activation validation.
- Operational inventory blocking.

Relevant existing components include:

- `HotelBookingAllocation`
- `DateWiseReserveRequestDto`
- `StayAvailabilityResponseDto`
- `HotelBookingAllocationRepository`
- `HotelInventoryService`
- `HotelController`

The existing stay availability calculation:

- Uses check-in inclusive and check-out exclusive semantics.
- Calculates the total number of nights.
- Checks availability across every occupied night.
- Calculates minimum availability across the stay.
- Resolves the effective price for each night.
- Returns nightly pricing breakdowns.
- Calculates the total price for the requested room quantity.

This functionality should be reused.

## 3.2 Booking Service

The existing booking service manages:

- Unique booking references.
- Customer information.
- Service type and service identification.
- Booking amount.
- Booking status.
- Booking details JSON.
- Booking expiry.
- Confirmation and cancellation.
- Refund-related fields.
- Hotel check-in and check-out lifecycle information.
- Cancellation policy snapshots.

Existing booking statuses:

- `INITIATED`
- `PENDING_PAYMENT`
- `CONFIRMED`
- `CANCELLED`
- `FAILED`
- `EXPIRED`

`BookingStateTransitionService` manages lifecycle transitions and notification outbox events.

The Booking entity should remain the parent reservation record.

## 3.3 Payment Service

The existing payment integration supports the payment lifecycle associated with booking confirmation.

The updated implementation must preserve:

- Existing payment order creation.
- Payment verification.
- Verified payment callbacks/webhooks.
- Refund processing.
- Late-payment safeguards.

A single consolidated payment must cover the complete hotel reservation.

## 3.4 Frontend

The existing traveller hotel booking implementation includes:

- Hotel search.
- Hotel result cards.
- Room listing and selection.
- Checkout modal.
- Reservation and payment integration.

The current checkout uses the legacy `reserveRooms` flow and static room pricing.

The new flow must use date-wise availability and reservation APIs instead.

# 4. Target Microservice Responsibilities

Service ownership must remain clearly separated.

| Service | Responsibility |
|---|---|
| Hotel Service | Room types, inventory, availability, pricing and room allocations |
| Booking Service | Reservation record, booking reference, expiry, confirmation and cancellation |
| Payment Service | Payment order, payment verification and refunds |
| Notification Service | Booking confirmation, cancellation and related notifications |
| Review Service | Hotel reviews and ratings |
| API Gateway | Routing and secured communication between services |

Important architectural rules:

1. Hotel Service owns inventory and pricing.
2. Booking Service must not independently calculate or modify hotel inventory.
3. Payment Service must not directly modify hotel inventory.
4. Booking Service owns the booking lifecycle.
5. Hotel Service owns the allocation lifecycle.
6. Payment confirmation must be verified before booking confirmation.
7. Existing service-to-service security and authentication mechanisms must be preserved.

# 5. Target Data Model

## 5.1 Booking – Parent Reservation

The existing `Booking` entity should continue to represent the complete hotel reservation.

For hotel bookings, the record must contain:

| Attribute | Description |
|---|---|
| bookingRef | Unique reservation reference |
| customerId | Traveller identifier |
| customerName | Primary guest name |
| customerEmail | Primary guest email |
| serviceType | HOTEL |
| serviceId | Hotel identifier |
| serviceTitle | Hotel name |
| totalAmount | Consolidated reservation amount |
| status | Booking lifecycle status |
| detailsJson | Structured reservation details |
| checkInDate | Stay start date |
| checkOutDate | Stay end date |
| bookingDate | Reservation creation timestamp |
| expiresAt | Reservation/payment expiry |
| confirmedAt | Confirmation timestamp |

Existing refund and cancellation fields should remain compatible.

## 5.2 Hotel Booking Allocation

The existing `HotelBookingAllocation` model must be extended to support multiple allocations under one booking reference.

Recommended conceptual structure:

| Attribute | Description |
|---|---|
| id | Unique allocation identifier |
| bookingRef | Parent booking reference |
| hotelId | Hotel identifier |
| roomId | Room type identifier |
| checkInDate | Stay start date |
| checkOutDate | Stay end date |
| roomCount | Number of rooms reserved for this room type |
| status | HELD, CONFIRMED or RELEASED |
| totalPrice | Total amount for this room-type allocation |
| nightlyRatesJson | Snapshot of effective nightly prices |

**Critical schema change:**

The existing unique constraint on `bookingRef` must be removed or redesigned.

Recommended uniqueness:

- Allocation ID remains the primary key.
- Multiple allocations may share the same booking reference.
- Idempotency must be enforced at the consolidated reservation operation level, not by restricting a booking to one allocation.

Example:

| Allocation | Booking reference | Room type | Quantity |
|---|---|---|---:|
| A-101 | TE-H-10001 | Deluxe | 2 |
| A-102 | TE-H-10001 | Family Suite | 1 |
| A-103 | TE-H-10001 | Premium | 1 |

A single reservation can therefore have multiple allocation records.

For multiple rooms of the same room type, `roomCount` represents the quantity. Individual guest assignments remain separate in the booking details.

## 5.3 Booking Details JSON

The existing `detailsJson` field should contain a versioned, structured representation of the hotel reservation.

Recommended conceptual payload:

```json
{
  "schemaVersion": 2,
  "hotelId": 101,
  "hotelName": "Example Hotel",
  "checkInDate": "2026-12-10",
  "checkOutDate": "2026-12-13",
  "totalNights": 3,
  "primaryGuest": {
    "fullName": "John Doe",
    "email": "john@example.com",
    "mobile": "+910000000000"
  },
  "roomAllocations": [
    {
      "roomTypeId": 201,
      "roomTypeName": "Deluxe Room",
      "rooms": [
        {
          "roomNumber": 1,
          "adults": 2,
          "children": 0,
          "childAges": [],
          "occupants": [
            { "fullName": "John Doe" },
            { "fullName": "Jane Doe" }
          ]
        },
        {
          "roomNumber": 2,
          "adults": 2,
          "children": 1,
          "childAges": [7],
          "occupants": [
            { "fullName": "Guest One" },
            { "fullName": "Guest Two" },
            { "fullName": "Child One" }
          ]
        }
      ],
      "quantity": 2,
      "totalPrice": 6000,
      "nightlyRates": [
        { "date": "2026-12-10", "price": 1000 },
        { "date": "2026-12-11", "price": 1000 },
        { "date": "2026-12-12", "price": 1000 }
      ]
    }
  ],
  "totalAmount": 6000
}
```

This is an illustrative contract, not a final serialization requirement.

Implementation must consider the existing `detailsJson` size limit of 4000 characters. If the new structured payload can exceed that limit, the storage strategy must be redesigned rather than silently truncating or losing guest information.

The final schema must be versioned and consistently interpreted by Booking Service and the frontend.

## 5.4 Pricing snapshots

Pricing information stored with a reservation must represent the price accepted by the traveller at booking time.

Historical booking pricing must remain immutable even when:

- Base room prices change.
- Date-specific overrides change.
- Room types are modified.
- Future inventory pricing is updated.

# 6. Inventory and Pricing Rules

## 6.1 Availability calculation

The existing inventory formula remains unchanged:

`Available = Total Physical Rooms - Confirmed Bookings - Active Holds - Operational Blocks`

Availability is calculated for each occupied date.

Check-in date is included.

Check-out date is excluded.

A room type is selectable only when it remains available throughout the requested stay.

## 6.2 Pricing calculation

The existing effective pricing hierarchy remains unchanged:

1. Date-specific price override.
2. Base room price when no override exists.

For a selected room type:

`Room Type Total = Sum of Effective Nightly Prices × Room Quantity`

For the complete reservation:

`Reservation Total = Sum of All Room Type Totals`

The backend must calculate and return authoritative prices.

The frontend must not use static `pricePerNight` values as the final booking amount.

## 6.3 Availability refresh

Availability must be rechecked:

- When the traveller changes dates.
- When the traveller changes room quantities.
- When a room type is added or removed.
- Before creating the reservation hold.
- When the reservation is submitted.

The frontend must display updated availability and pricing from the backend.

# 7. Multi-Allocation Reservation Architecture

This is the most important backend enhancement.

## 7.1 Existing limitation

Currently, the hotel inventory service reserves one room type under a booking reference.

The repository lookup and uniqueness constraint assume that a booking reference maps to one allocation.

This cannot support a reservation containing multiple room types.

## 7.2 Required consolidated reservation operation

Introduce a consolidated hotel reservation operation that accepts all selected room types in one request.

Conceptual request:

```json
{
  "bookingRef": "TE-H-10001",
  "hotelId": 101,
  "checkInDate": "2026-12-10",
  "checkOutDate": "2026-12-13",
  "allocations": [
    {
      "roomTypeId": 201,
      "quantity": 2
    },
    {
      "roomTypeId": 202,
      "quantity": 1
    }
  ]
}
```

The exact endpoint and DTO names must follow the existing project conventions.

## 7.3 Atomic reservation requirement

All selected room allocations must be held atomically within Hotel Service.

Expected process:

1. Validate the hotel.
2. Validate all selected room types.
3. Verify all room types are ACTIVE.
4. Verify the property is ACTIVE.
5. Validate stay dates.
6. Validate room quantities.
7. Acquire inventory locks for all required room types and occupied dates.
8. Resolve effective nightly prices.
9. Validate availability for every selected allocation.
10. Calculate each allocation's total.
11. Create all allocation records.
12. Increment held inventory for every required date.
13. Commit the transaction.
14. Return the complete consolidated reservation result.

**Failure requirement:**

If any room type or occupied date is unavailable, the entire reservation attempt must fail.

No partial inventory reservation may remain.

## 7.4 Transaction and concurrency considerations

The consolidated operation must use one database transaction inside Hotel Service.

Inventory rows must retain their existing concurrency protection.

When multiple room types and dates are involved:

- Lock records in a deterministic order.
- Prevent concurrent requests from overselling inventory.
- Ensure transaction rollback reverses every held inventory update.
- Prevent duplicate holds on retries.

A deterministic lock order should be used to reduce deadlock risk.

## 7.5 Idempotency

The consolidated reservation operation must be idempotent.

If the same reservation request is retried with the same booking reference:

- It must not create duplicate allocations.
- It must not increment held inventory again.
- It must return the existing reservation result when the request matches.
- A conflicting request using an already-used booking reference must be rejected.

A RELEASED reservation must not be silently recreated using the same booking reference.

The implementation must define and persist a reliable request identity or payload fingerprint where necessary.

## 7.6 Allocation lifecycle operations

All allocation lifecycle operations must work across every allocation belonging to a booking reference.

Required operations:

- Find allocations by booking reference.
- Confirm all held allocations.
- Release all held allocations.
- Retrieve complete allocation details.
- Check allocation lifecycle state.
- Handle expiry and retries safely.

A failure during confirmation or release must not leave the reservation in an inconsistent state. The service should use transaction boundaries and idempotent recovery operations.

# 8. Distributed Booking and Payment Lifecycle

Hotel Service and Booking Service use separate databases.

Therefore, a single database transaction cannot atomically commit both services.

The overall booking flow must use coordinated service operations, compensating actions and idempotent lifecycle transitions.

## 8.1 Reservation initiation

Recommended sequence:

1. Traveller submits the selected room allocations and guest information.
2. Booking Service generates a unique booking reference and creates an INITIATED booking record.
3. Booking Service requests a consolidated inventory hold from Hotel Service.
4. Hotel Service atomically holds all selected room allocations.
5. Hotel Service returns the consolidated price and allocation information.
6. Booking Service stores the structured reservation details and authoritative amount.
7. Booking status transitions to PENDING_PAYMENT.
8. Booking expiry is established using the existing 15-minute hold window.
9. Payment Service creates one payment order for the consolidated amount.

If the inventory hold fails, the booking must not proceed to payment.

If Booking Service cannot persist the successful hold result, it must initiate compensation by releasing the hotel reservation.

## 8.2 Payment verification

After the traveller completes payment:

1. Payment Service verifies the payment using the existing verification mechanism.
2. Booking Service receives the verified payment result.
3. Booking Service validates the current booking state and expiry.
4. Booking Service requests confirmation of all hotel allocations.
5. Hotel Service confirms all held allocations.
6. Booking Service transitions the reservation to CONFIRMED.
7. Existing notification mechanisms publish the booking confirmation event.

A frontend success redirect alone must never be treated as payment confirmation.

## 8.3 Payment failure

When payment fails:

- Booking must not become CONFIRMED.
- All held allocations must be released.
- Booking lifecycle must transition according to the existing failure rules.
- The traveller must receive an appropriate failure message.

## 8.4 Reservation expiry

The existing 15-minute reservation hold must be preserved.

When the hold expires without verified successful payment:

- All held allocations must be released.
- The booking must transition to EXPIRED.
- Expired reservations must not appear as successful bookings in My Bookings.
- Inventory must become available again.

## 8.5 Late payment callback

A delayed successful payment callback must not confirm an expired or released reservation.

The existing late-payment safeguard must be retained.

If payment succeeds after the reservation can no longer be confirmed:

- The booking must remain unconfirmed.
- Hotel allocations must not be recreated automatically.
- The existing compensation/refund mechanism must handle the payment.

## 8.6 Cancellation

Cancellation must operate on the parent booking reference.

Recommended sequence:

1. Validate that the booking is eligible for cancellation.
2. Calculate the applicable refund using the stored cancellation policy snapshot.
3. Initiate the existing refund workflow.
4. Release every associated confirmed hotel allocation.
5. Transition the booking to CANCELLED according to the established lifecycle rules.
6. Publish cancellation notification events.

The implementation must handle retries and partial service failures without double-refunding or releasing inventory twice.

The existing cancellation lifecycle should be audited before modifying transition ordering.

# 9. Proposed Frontend Booking Journey

The traveller experience should be organized into the following stages.

## Stage 1 – Hotel Search

Search fields:

- Destination.
- Check-in date.
- Check-out date.
- Adults.
- Children.
- Children's ages.
- Number of rooms.

The search interface must provide an intuitive guest and room selector.

The backend search contract should be extended where occupancy filtering is required.

## Stage 2 – Search Results

Search results should focus on hotel discovery.

Display:

- Hotel name.
- Cover image.
- Star rating.
- Location.
- Key amenities.
- Short description.
- Relevant price indication.
- View Details action.

Provide suitable filtering and sorting options.

Room selection should move to the hotel details experience.

## Stage 3 – Hotel Details

Provide a dedicated hotel details page.

Recommended sections:

- Hotel image gallery.
- Property overview.
- Address and location.
- Facilities and amenities.
- Hotel policies.
- Check-in/check-out information.
- Cancellation policy.
- Reviews and ratings.
- Available room types.

This follows the existing business decision that room types, prices and availability should be displayed on the hotel details page.

## Stage 4 – Room Selection

Display available room types for the selected dates.

Each room type should show:

- Room type name.
- Images.
- Capacity.
- Amenities.
- Maximum occupancy.
- Available quantity.
- Effective nightly pricing.
- Total stay price.
- Date-wise price breakdown where applicable.

The traveller can select multiple room types and quantities.

The interface must maintain a consolidated selection summary.

## Stage 5 – Guest Allocation

Generate a separate guest configuration for every selected room.

For each room:

- Adults.
- Children.
- Individual children's ages.
- Occupant names.

Validate maximum occupancy and child-age requirements.

The interface must make it clear which occupants belong to which room.

## Stage 6 – Primary Guest Details

Collect the primary guest's:

- Full name.
- Email.
- Mobile number.

These details apply to the entire reservation.

## Stage 7 – Review and Payment

Display:

- Hotel details.
- Stay dates.
- Number of nights.
- Selected room types.
- Room quantities.
- Guest allocation.
- Primary guest information.
- Nightly rates.
- Date-specific pricing differences.
- Individual room-type totals.
- Consolidated reservation amount.
- Cancellation policy.

The final amount must match the authoritative backend calculation.

Payment must use one consolidated payment order.

## Stage 8 – Booking Confirmation

Display:

- Booking reference.
- Hotel details.
- Stay dates.
- Primary guest information.
- Selected room types and quantities.
- Guest allocation by room.
- Consolidated amount.
- Payment status.
- Cancellation policy.

The confirmation page must be displayed only after the booking has been successfully confirmed.

## Stage 9 – My Bookings

Only successfully confirmed bookings should appear in the normal successful-bookings listing.

Each hotel booking should expose:

- Reservation details.
- Room allocations.
- Guest information.
- Stay dates.
- Payment information.
- Cancellation eligibility.
- Cancellation action.

Expired and unsuccessful payment attempts must not be represented as successful reservations.

# 10. API Contract Requirements

The following are proposed logical operations. Exact endpoint paths and DTO names should be finalized after inspecting existing controller mappings and conventions.

## Hotel Service

| Operation | Purpose |
|---|---|
| Search hotels | Search properties using destination and stay dates |
| Check stay availability | Check date-wise availability and pricing |
| Reserve consolidated stay | Atomically hold multiple room-type allocations |
| Retrieve reservation allocations | Retrieve every allocation under a booking reference |
| Confirm consolidated stay | Confirm all held allocations |
| Release consolidated stay | Release all held allocations |

## Booking Service

| Operation | Purpose |
|---|---|
| Create hotel booking | Create parent reservation |
| Update reservation details | Persist guest and allocation snapshots |
| Retrieve booking | Retrieve reservation details |
| Confirm booking | Confirm after verified payment and inventory confirmation |
| Expire booking | Handle reservation expiry |
| Cancel booking | Cancel entire reservation |

## Payment Service

Existing payment operations should be reused.

The payment order must represent the consolidated hotel reservation amount.

No separate payment order should be created for each room type.

# 11. Validation Requirements

## 11.1 Search validation

- Destination must be valid.
- Check-in must precede check-out.
- Dates must satisfy existing booking restrictions.
- Guest and room quantities must be positive and valid.

## 11.2 Room selection validation

- Selected room type must belong to the chosen hotel.
- Room type must be ACTIVE.
- Hotel must be ACTIVE.
- Requested quantity must be positive.
- All occupied dates must have sufficient inventory.
- Availability must be rechecked before reservation.

## 11.3 Guest validation

- Every selected room must have at least one valid occupant.
- Adult and child counts must be valid.
- Occupancy must not exceed the applicable room-type capacity.
- Every child must have an individual age.
- Occupant information must correspond to the configured guest counts.
- Primary guest contact details must be valid.

## 11.4 Pricing validation

- Effective nightly prices must come from Hotel Service.
- Date-specific overrides must take precedence over base prices.
- Quantity must be included in calculations.
- Consolidated amount must equal the sum of allocation totals.
- Historical booking prices must remain unchanged.

## 11.5 Payment validation

- Payment must be verified server-side.
- Payment amount must match the booking amount.
- Duplicate callbacks must not create duplicate confirmation.
- Expired reservations must not be confirmed by late callbacks.

# 12. Failure Handling and Recovery

| Failure scenario | Expected behavior |
|---|---|
| One room type unavailable | Reject entire reservation attempt |
| One occupied date unavailable | Reject entire reservation attempt |
| Inventory transaction fails | Roll back every allocation and held inventory update |
| Duplicate reservation request | Return existing result without double-holding |
| Booking persistence fails after inventory hold | Compensate by releasing the hold |
| Payment creation fails | Release held inventory and fail the booking according to lifecycle rules |
| Payment fails | Release all held allocations |
| Hold expires | Release all allocations and expire booking |
| Payment succeeds after expiry | Do not confirm; initiate compensation/refund |
| Allocation confirmation fails | Prevent false booking confirmation and execute recovery |
| Cancellation request is retried | Do not double-refund or double-release |
| Notification delivery fails | Preserve booking state; retry notification independently |

The architecture must not rely on a single synchronous request succeeding across all microservices.

Service operations should be idempotent, and recovery must be possible after partial distributed failures.

# 13. Security and Data Integrity

- Preserve existing authentication and authorization mechanisms.
- Verify traveller ownership before accessing or cancelling a booking.
- Validate hotel and room-type relationships on the backend.
- Do not trust client-submitted prices or calculated totals.
- Do not expose another traveller's guest information.
- Validate all guest information before persistence.
- Protect service-to-service reservation operations.
- Ensure booking references cannot be used to access or modify unrelated reservations.
- Preserve auditability of reservation lifecycle transitions.

# 14. Integration and Testing Strategy

The implementation must include tests covering the following scenarios.

## 14.1 Inventory tests

- Reserve one room type.
- Reserve multiple rooms of the same type.
- Reserve multiple room types under one booking reference.
- Reserve different quantities for multiple room types.
- Fail when one selected room type is unavailable.
- Fail when one occupied date is sold out.
- Roll back all allocations after failure.
- Verify concurrency protection.
- Verify duplicate request idempotency.
- Confirm all allocations.
- Release all allocations.
- Verify released inventory becomes available again.

## 14.2 Pricing tests

- Base pricing.
- Date-specific pricing overrides.
- Different nightly prices within one stay.
- Multiple rooms of the same type.
- Multiple room types.
- Consolidated amount calculation.
- Historical booking pricing immutability.

## 14.3 Guest tests

- One room with multiple adults.
- One room with children.
- Multiple children with different ages.
- Multiple room types with different occupancy configurations.
- Occupancy exceeding room capacity.
- Missing child ages.
- Invalid occupant count.
- Missing primary guest information.

## 14.4 Booking and payment tests

- Successful reservation and payment.
- Failed payment.
- Payment order creation failure.
- Duplicate payment callbacks.
- Expired reservation.
- Late successful payment.
- Allocation confirmation failure.
- Booking persistence failure after successful hold.
- Consolidated booking retrieval.

## 14.5 Cancellation tests

- Full cancellation before 48 hours.
- Full cancellation between 24 and 48 hours.
- Full cancellation within 24 hours.
- Check-in-day cancellation after 14:00.
- Cancellation of all associated allocations.
- Duplicate cancellation requests.
- Refund and inventory release recovery.

## 14.6 Frontend integration tests

- Hotel search with guest and room selection.
- Hotel details navigation.
- Multiple room-type selection.
- Room quantity changes.
- Individual guest allocation.
- Dynamic children's age inputs.
- Primary guest information.
- Correct nightly and consolidated pricing.
- Payment success and failure.
- Confirmation page.
- My Bookings.
- Whole-booking cancellation.

# 15. Implementation Work Packages

The following work packages are recommended.

| Work package | Scope |
|---|---|
| WP-HB-01 | Hotel discovery and search enhancement |
| WP-HB-02 | Dedicated hotel details experience |
| WP-HB-03 | Multi-room selection and date-wise pricing |
| WP-HB-04 | Backend multi-allocation inventory architecture |
| WP-HB-05 | Booking and consolidated payment lifecycle |
| WP-HB-06 | Guest allocation and checkout experience |
| WP-HB-07 | Booking confirmation, history and cancellation |
| WP-HB-08 | End-to-end integration and verification |

## Recommended implementation sequence

**First: Backend architecture**

WP-HB-04 must establish the consolidated multi-allocation reservation contract and atomic inventory operations.

**Second: Booking and payment integration**

WP-HB-05 must coordinate the parent booking, inventory allocations, payment and expiry lifecycle.

**Third: Frontend experience**

WP-HB-01, WP-HB-02 and WP-HB-03 can be designed and implemented while backend contracts are being finalized, but must integrate against the agreed contracts.

WP-HB-06 and WP-HB-07 should then complete the guest checkout and booking lifecycle experience.

**Finally: End-to-end verification**

WP-HB-08 validates the complete traveller journey, including concurrency, payment failures, expiry, compensation and cancellation.

# 16. Non-Negotiable Implementation Constraints

1. Do not replace the existing microservice architecture with a monolithic implementation.
2. Do not introduce a separate payment for each room type.
3. Do not allow partial inventory reservation for a consolidated booking.
4. Do not calculate final hotel prices using static frontend values.
5. Do not bypass existing date-wise inventory and pricing services.
6. Do not confirm a booking based only on a frontend payment-success redirect.
7. Do not allow expired reservations to be confirmed by late callbacks.
8. Do not show pending or expired reservations as successful bookings in My Bookings.
9. Do not implement partial room cancellation in the initial release.
10. Preserve existing booking, refund, notification and hotel-manager functionality.
11. Preserve historical booking references and pricing snapshots.
12. Do not introduce unnecessary duplicate APIs or service responsibilities.
13. Maintain backward compatibility with existing booking records and APIs where required.
14. Implement distributed failure recovery using idempotent operations and compensating actions.
15. Do not modify unrelated modules or redesign unrelated interfaces.

# 17. Acceptance Criteria

The updated traveller hotel booking flow will be considered functionally complete when:

- A traveller can search hotels using destination, dates, guests and rooms.
- The traveller can view hotel details and date-wise available room types.
- Multiple room types and quantities can be selected.
- Guest information can be configured independently for every room.
- Every child has an individually captured age.
- One primary guest can be specified for the entire reservation.
- One booking reference represents the complete reservation.
- All room allocations are held atomically.
- Effective nightly pricing is calculated by Hotel Service.
- One consolidated payment is processed.
- Successful payment confirms all allocations and the parent booking.
- Failed or expired reservations release all associated inventory.
- Late payment callbacks cannot confirm expired reservations.
- My Bookings displays only confirmed successful reservations.
- Whole-booking cancellation releases all associated allocations and follows the existing refund policy.
- The complete flow passes integration and end-to-end verification.

---

# 18. Final Architecture Direction

TravelEasy will use a **parent reservation with multiple room-type allocations**.

The Booking Service will own the parent booking and its lifecycle.

The Hotel Service will own the room allocations, date-wise inventory and pricing snapshots.

The Payment Service will process one consolidated payment.

Guest details will be stored as structured reservation information, with individual room-level occupancy and occupant assignments.

The complete reservation will behave as one logical booking even though its inventory is represented by multiple allocation records.

This design preserves the existing microservice responsibilities while extending the reservation model to support realistic multi-room hotel bookings.
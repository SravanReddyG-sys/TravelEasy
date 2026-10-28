# TravelEasy Route Specification

## Route Data Model, Constraints, and Business Rules

**Document status:** Agreed product requirements baseline for design and
architecture review\
**Product:** TravelEasy --- Bus Operator Dashboard\
**Scope:** Route data model and route-management behavior\
**Implementation status:** Specification only; no code changes are
authorized by this document

------------------------------------------------------------------------

## 1. Purpose

This specification defines the proposed route data model, integrity
constraints, ownership rules, lifecycle behavior, fare principles, and
validation requirements for TravelEasy.

A **route** is a reusable journey definition made up of an origin, an
ordered sequence of intermediate stops, and a destination. Each stop can
offer multiple boarding points and multiple dropping points. A
**schedule** assigns an eligible bus to a route for a specific trip and
stores that trip's actual timings.

This specification is the baseline for designing the Routes landing
page, Create/Edit Route workflow, and the integration between Routes and
Schedules. Before implementation, the current codebase and database
schema must be audited so existing entities and behavior can be reused
wherever appropriate.

## 2. Confirmed product decisions

The following decisions are confirmed and must guide the next
route-module design.

1.  **Actual stop timings are schedule-specific.** Routes define ordered
    stops and point options; schedules store actual trip timings.
2.  **Multiple boarding and dropping points are supported per stop.**
    These are independently configured and validated.
3.  **Repeated physical locations are disallowed within a route.** A
    route cannot visit the same physical location more than once.
4.  **Structural route edits are blocked while future schedules depend
    on the route.** Dependencies must be resolved before structural
    edits are allowed.
5.  **Default fares are segment-based.** The fare between two stops is
    the sum of consecutive segment fares, with permitted manual
    overrides for applicable origin--destination pairs.

These are product constraints. The exact persistence design must be
checked against the existing TravelEasy schema before implementation.

## 3. Core concepts and separation of responsibilities

### 3.1 Route

A reusable definition of a journey. It owns or references: - Route
identity and operator ownership - Origin and destination - Ordered route
stops - Boarding and dropping point options at each stop - Route
lifecycle status

A route does **not** own the actual timings of each trip.

### 3.2 Schedule

An operational trip that references one route and an eligible bus. It
owns: - Operating date or recurrence - Actual trip departure, arrival,
and stop timings - Schedule lifecycle status - Schedule-specific fare
configuration, as supported by the approved fare model

One route can be used by multiple schedules. Creating a schedule must
not create a duplicate route. Cancelling a schedule must not delete or
deactivate the route.

### 3.3 Location

A reusable physical location, such as a city, terminal, or bus station.
Location identity must be separate from the route-stop record so the
same location can be referenced by different routes.

### 3.4 Route stop

A route-specific occurrence of a location with an explicit sequence
number and stop type.

### 3.5 Boarding and dropping points

Boarding and dropping points represent independently selectable pickup
and drop-off locations associated with a route stop. Multiple points of
each type may exist at one stop.

### 3.6 Segment fare and fare override

A segment fare defines the fare for travel between consecutive stops. A
manual override may replace the calculated fare for an applicable
origin--destination pair, subject to an explicitly defined scope and
validity.

------------------------------------------------------------------------

## 4. Proposed logical data model

The following entities are a logical model, not a claim about the
current database schema. Existing TravelEasy entities should be reused
when they already satisfy the requirements.

### 4.1 Route

  -----------------------------------------------------------------------------
  Field                         Requirement             Constraints
  ----------------------------- ----------------------- -----------------------
  `id`                          Required                Unique, immutable
                                                        primary key

  `operator_id`                 Required                Derived from
                                                        authenticated identity
                                                        and server-side
                                                        ownership

  `route_code`                  Recommended             Unique within the
                                                        agreed scope;
                                                        database-enforced

  `route_name`                  Recommended             Nonblank,
                                                        human-readable label

  `origin_route_stop_id`        Logical requirement     If stored explicitly,
                                                        must identify the first
                                                        stop of this route

  `destination_route_stop_id`   Logical requirement     If stored explicitly,
                                                        must identify the last
                                                        stop of this route

  `status`                      Required                Controlled lifecycle
                                                        value

  `created_at`                  Required                Server-generated
                                                        timestamp

  `updated_at`                  Required                Server-maintained
                                                        timestamp

  `version`                     Recommended             Supports optimistic
                                                        concurrency during
                                                        edits
  -----------------------------------------------------------------------------

Constraints: - A route belongs to one operator unless the product
explicitly supports shared/global routes. - A complete active route has
at least two distinct ordered stops. - Origin and destination are
distinct route-stop records belonging to the same route. - A route
cannot be activated until its required fields, stops, and point
configuration are valid. - Route-code uniqueness must be enforced at a
defined scope. - If origin/destination IDs are stored as well as stop
ordering, their consistency must be enforced. Alternatively, derive
origin and destination from the first and last stops.

### 4.2 RouteStop

  -----------------------------------------------------------------------
  Field                   Requirement             Constraints
  ----------------------- ----------------------- -----------------------
  `id`                    Required                Unique route-stop
                                                  identifier

  `route_id`              Required                Foreign key to the
                                                  parent route

  `location_id`           Required                Foreign key to a valid
                                                  physical location

  `sequence_number`       Required                Positive integer,
                                                  unique within a route

  `stop_type`             Required                `ORIGIN`,
                                                  `INTERMEDIATE`, or
                                                  `DESTINATION`

  `created_at`            Recommended             Audit timestamp
  -----------------------------------------------------------------------

Constraints: 1. Sequence numbers define an unambiguous journey order. 2.
A complete active route has exactly one origin and exactly one
destination. 3. The origin has the lowest sequence number; the
destination has the highest. 4. Intermediate stops are between the
origin and destination. 5. Duplicate sequence numbers within a route are
prohibited. 6. Repeated physical locations within one route are
prohibited, even if assigned different sequence numbers. 7. Reordering
must be atomic and must not silently invalidate schedules or bookings.

Recommended database constraints: - Unique
`(route_id, sequence_number)`. - Enforce physical-location uniqueness
within a route, using the canonical location ID, where compatible with
the chosen schema.

### 4.3 Location

Recommended fields: - `id` - `name` - `location_type` - Structured
address fields, if required - `latitude` and `longitude`, if map-based
functionality requires them - Active/status field

Constraints: - A selected location must exist and be eligible for route
use. - Free-text display names must not be used as authoritative
relational identifiers. - Equivalent spellings should not
unintentionally create duplicate canonical locations. - Multiple
terminals in the same city must be distinguishable as separate physical
locations. - The policy for whether operators can create locations or
must select from a controlled catalog must be confirmed against the
current system.

### 4.4 BoardingPoint and DroppingPoint

Boarding and dropping are separate capabilities, even when they refer to
the same physical place.

Recommended fields for each point: - `id` - `route_stop_id` -
`point_name` - `location_id` or structured physical address - `active` -
Optional passenger instructions

A shared physical-point entity with separate boarding/drop-off
capability associations is acceptable if the two capabilities remain
independently enforceable.

Constraints: - Every point belongs to a valid route stop. - A point
cannot be selected for a stop outside the selected route. - Points use
stable IDs, not display labels, as relational references. - Multiple
boarding points and multiple dropping points per stop are supported. -
Inactive points cannot be selected for new schedules or bookings. -
Historical references to deactivated points remain interpretable. -
Deletion of referenced points must be blocked or handled through an
approved migration/reassignment process. - Required point capabilities
must be validated for the stop role and product policy.

Recommended default: - The origin must provide at least one boarding
point. - The destination must provide at least one dropping point. -
Whether origin stops can accept dropping and destination stops can
accept boarding should be confirmed as an explicit product rule.

------------------------------------------------------------------------

## 5. Schedule-specific stop timings

**Actual stop timings belong to each schedule, not the reusable route.**

A schedule-stop record maps a route stop to a specific schedule.
Depending on the existing schema, arrival and departure times may be
stored directly on the schedule-stop record or in separate timing
records.

Potential fields: - `schedule_id` - `route_stop_id` -
`sequence_number` - `arrival_time` - `departure_time` - `boarding_time`,
if distinct from departure - `dropping_time`, if distinct from arrival -
`service_day_offset`, where needed for overnight trips

Constraints: - Every schedule-stop record references a stop on the route
assigned to that schedule. - Schedule stop order must match the route
order. - Times must use the schedule's operating timezone. - Later stops
must occur later in the trip timeline. - Overnight trips must be
supported; later stops may fall on the next calendar day. - A clock time
alone is insufficient to represent the day of an overnight stop. -
Arrival must not be after departure at the same stop when both are
recorded. - Boarding must not occur after departure from that stop. -
Dropping must not occur before arrival at that stop. - Timing
constraints must be validated by the backend. - Timing edits must not
silently overwrite historical timing information associated with
confirmed bookings.

Avoid storing the same timing concept twice without defining one source
of truth. If boarding equals departure or dropping equals arrival by
definition, derive the value rather than duplicating it. If they are
distinct operational events, store them separately and validate their
ordering.

------------------------------------------------------------------------

## 6. Route-to-schedule constraints

1.  A route can have zero or more schedules.
2.  A schedule references exactly one route unless multi-route schedules
    are explicitly supported.
3.  A schedule must use a bus owned by the authenticated operator.
4.  The bus must be eligible for the schedule's service date and time.
5.  An inactive route cannot be used to create a new schedule.
6.  An inactive bus cannot be assigned to a new schedule.
7.  The route must have a valid ordered stop list before scheduling.
8.  Schedule stops must correspond to the selected route.
9.  Creating a schedule must not create or duplicate a route.
10. Cancelling a schedule must not delete or cancel the route.

### Historical integrity

-   Structural changes affecting future schedules are blocked until
    dependencies are resolved.
-   Enough historical information must be preserved to interpret the
    route and timings associated with existing bookings.
-   Stops referenced by active schedules or confirmed bookings must not
    be destructively changed.
-   Propagation of route changes to schedules requires explicit
    validation and an approved workflow.
-   Ordinary route edits must not alter booking inventory, seat
    allocation, payment state, or refund state.

The implementation may use schedule-specific stop snapshots or versioned
routes, provided historical integrity is guaranteed and the current
architecture is considered.

------------------------------------------------------------------------

## 7. Confirmed structural edit policy

Structural route edits include: - Adding or removing a stop - Reordering
stops - Changing the physical location of a stop - Changing route
endpoints - Changing point definitions in a way that invalidates
dependent schedules

**Policy:** block structural edits while future schedules depend on the
route, until those dependencies have been resolved.

The system should: 1. Identify affected future schedules. 2. Explain why
the edit is blocked. 3. Provide the operator with relevant schedule
references. 4. Require the operator to resolve dependencies through
approved schedule workflows. 5. Revalidate the route and its points
before accepting the structural change.

Resolving dependencies must not automatically cancel trips, refund
bookings, reassign buses, or modify bookings. Those actions follow their
own approved business workflows.

The exact definition of "future schedule dependency" (for example,
whether cancelled schedules or schedules with no bookings block an edit)
must be made explicit during implementation and aligned with existing
lifecycle states.

------------------------------------------------------------------------

## 8. Origin-to-destination fares

Because routes support intermediate stops, passengers may travel between
eligible stops rather than only from the first stop to the last.

Constraints: - A journey selects a valid boarding stop/point and
dropping stop/point. - Boarding must occur before dropping in route
order. - The same stop cannot be both origin and destination. -
Reverse-direction travel requires a separately defined reverse route or
another explicitly supported directional model. - Fare calculation uses
the approved origin-to-destination pricing rules. - Confirmed bookings
retain the fare applicable when the booking was confirmed. - Route edits
must not silently recalculate confirmed fares.

### 8.1 Segment fare calculation

The confirmed default rule is:

`Fare(i, j) = sum of SegmentFare(k, k+1) for k = i through j-1`

This means the default fare from stop `i` to stop `j` is the sum of all
consecutive segment fares between those stops.

### 8.2 Manual overrides

A valid manual override may replace the calculated fare for an
applicable origin--destination pair.

Before implementation, define: - Override scope (route-wide or
schedule-specific) - Effective dates or validity window, if required -
Whether overrides apply to all schedules or only selected schedules -
Precedence when an override conflicts with a segment-based calculation -
Currency, precision, minimum/maximum fare validation, and rounding
policy - Historical fare snapshot behavior for confirmed bookings

If the current backend only supports a single fare per schedule, segment
fares and overrides are a separately scoped enhancement. Do not
introduce them implicitly as part of a route UI change.

------------------------------------------------------------------------

## 9. Ownership and authorization

Backend authorization is mandatory.

  -----------------------------------------------------------------------
  Operation                           Authorization
  ----------------------------------- -----------------------------------
  View route                          Owning operator or authorized
                                      administrator

  Create route                        Authorized bus operator

  Edit route                          Owning operator or authorized
                                      administrator

  Add/reorder stops                   Owning operator or authorized
                                      administrator

  Manage boarding/drop-off points     Owning operator or authorized
                                      administrator

  Deactivate route                    Authorized owner/admin, subject to
                                      lifecycle rules

  Assign route to schedule            Authorized operator, eligible route
                                      and bus

  View associated schedules           Authorized owner or administrator
  -----------------------------------------------------------------------

Security constraints: - Never trust a frontend-supplied `operator_id` as
proof of ownership. - Resolve ownership from authenticated identity and
server-side records. - Verify ownership through the parent route for
every child operation. - Stop or point IDs must not bypass parent-route
authorization. - Direct API calls must not expose another operator's
route data. - Missing, malformed, or invalid identity claims must fail
closed. - Return suitable unauthorized/forbidden responses without
leaking another operator's data.

------------------------------------------------------------------------

## 10. Lifecycle and deletion

Suggested states are `DRAFT`, `ACTIVE`, and `INACTIVE`, subject to
existing TravelEasy status conventions.

  -----------------------------------------------------------------------
  Action/state                        Constraint
  ----------------------------------- -----------------------------------
  Draft                               May be incomplete if drafts are
                                      supported; cannot be scheduled
                                      until valid

  Activate                            Requires valid endpoints, ordered
                                      stops, and required point
                                      configuration

  Edit                                Must preserve schedule and booking
                                      integrity

  Deactivate                          Blocks new schedule assignments

  Delete                              Allowed only when protected
                                      references do not exist, or through
                                      approved archival

  Reactivate                          Requires revalidation
  -----------------------------------------------------------------------

Prefer soft deactivation over hard deletion once a route has been used
by schedules or bookings.

Before deactivation, evaluate future schedules, active bookings,
confirmed inventory, pending cancellation workflows, and point validity.
If unresolved future trips exist, block deactivation or require an
approved resolution process. Route deactivation must not silently cancel
trips or issue refunds.

------------------------------------------------------------------------

## 11. Database integrity and transactions

Required or recommended database constraints: - Primary keys for routes,
route stops, locations, and boarding/drop-off points. - Foreign keys for
parent-child relationships. - Unique `(route_id, sequence_number)`. -
Enforce unique physical location within a route, consistent with the
no-repeat policy. - Define a uniqueness scope for route codes. -
Non-null ownership and required parent references. - Check constraints
for positive sequence numbers and supported statuses where available. -
Indexes on `operator_id`, `route_id`, `schedule_id`, and frequently
queried foreign keys. - Indexes supporting ordered stop retrieval and
point lookup.

### Atomic route updates

Creating or editing a route with multiple stops must be atomic: 1.
Validate the request and authorization. 2. Validate locations and point
definitions. 3. Validate the full ordered sequence. 4. Persist the route
and child records in a transaction. 5. Commit only when all required
records pass validation.

If a stop update fails, the route must not remain partially reordered.
Use optimistic locking, version checks, or an equivalent concurrency
strategy to prevent lost updates.

### Performance

-   Paginate route lists and support filtering.
-   Avoid loading every child entity for the Routes landing page.
-   Fetch route details and ordered stops efficiently.
-   Avoid issuing one schedule-count query per route.
-   Index common ownership and parent-child lookup paths.

------------------------------------------------------------------------

## 12. API responsibilities

These are proposed responsibilities, not confirmed existing endpoints.
Follow existing TravelEasy endpoint conventions.

  -----------------------------------------------------------------------
  Operation                           Responsibility
  ----------------------------------- -----------------------------------
  Create route                        Validate ownership, endpoints,
                                      stops, order, and points

  List routes                         Return routes visible to the
                                      authenticated operator

  Get route details                   Return route, ordered stops,
                                      points, status, and schedule count

  Update route                        Apply validated changes without
                                      corrupting references

  Reorder stops                       Validate and atomically persist the
                                      new order

  Add/edit boarding point             Verify the point belongs to an
                                      authorized route stop

  Add/edit dropping point             Verify the point belongs to an
                                      authorized route stop

  Deactivate route                    Check schedule and booking
                                      dependencies

  List route schedules                Return authorized schedules for the
                                      route
  -----------------------------------------------------------------------

Every request must validate required fields, role and ownership, valid
IDs, unique stop order, physical-location uniqueness, point
associations, supported status values, and conflicting child records.
Failed requests must not partially mutate persisted data. Return
actionable validation errors without exposing raw database exceptions.

------------------------------------------------------------------------

## 13. Edge cases

  -----------------------------------------------------------------------
  Scenario                            Expected behavior
  ----------------------------------- -----------------------------------
  Route has one stop                  Reject as invalid

  Origin and destination are          Reject unless circular routes are
  identical                           explicitly supported

  Duplicate stop sequence             Reject

  Same physical location appears      Reject
  twice on a route                    

  Point belongs to another route      Reject

  Inactive point selected for a new   Reject
  booking                             

  Schedule references a stop outside  Reject
  its route                           

  Structural edit while future        Block until dependencies are
  schedules depend on route           resolved

  Overnight trip                      Preserve day offset and
                                      chronological order

  Same route name used by two         Allow unless names are globally
  operators                           unique by policy

  Concurrent reorder operations       Detect conflict; do not silently
                                      lose an update

  Existing booking references a       Preserve historical interpretation
  removed stop                        

  Boarding/drop-off points share a    Distinguish by stable ID and
  display name                        physical location

  Schedule timing changes after       Preserve applicable booking history
  booking                             and follow the approved
                                      update/notification workflow
  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 14. UI requirements

### Routes landing page

Display route name/code, origin, destination, intermediate-stop count,
status, and associated schedule count. Provide search, filtering,
pagination, and a **Create Route** action.

### Create/Edit Route workflow

-   Provide an ordered stop editor.
-   Support adding, editing, removing, and reordering stops.
-   Clearly identify origin, intermediate stops, and destination.
-   Prevent duplicate physical locations within the same route.
-   Validate required boarding/drop-off point configuration for each
    stop.
-   Display clear validation errors and explain structural-edit
    restrictions when future schedules depend on the route.

### Boarding/drop-off configuration

Within each stop, allow multiple boarding points and multiple dropping
points. Manage them independently and show point name, physical
location, and active status.

### Schedule handoff

Show associated schedules and keep actual timings in the schedule
workflow. Schedule creation must select an existing valid route and an
eligible bus.

------------------------------------------------------------------------

## 15. Acceptance criteria

The route model is ready for implementation review when:

-   [ ] Route and schedule are separate entities with a defined
    relationship.
-   [ ] Every active route has one valid origin and destination.
-   [ ] Intermediate stops are supported in a stable explicit order.
-   [ ] Repeated physical locations are rejected within a route.
-   [ ] Multiple boarding and dropping points per stop are supported
    independently.
-   [ ] Actual stop timings are schedule-specific.
-   [ ] Overnight trips and chronological validation are supported.
-   [ ] Structural route edits are blocked while future schedule
    dependencies remain unresolved.
-   [ ] Default fares are calculated from consecutive segment fares.
-   [ ] Manual origin--destination fare overrides follow a documented
    scope and precedence policy.
-   [ ] Backend ownership and child-record authorization are enforced.
-   [ ] Database constraints prevent invalid ordering and broken
    references.
-   [ ] Historical bookings and inventory remain interpretable after
    route changes.
-   [ ] API validation failures do not partially mutate persisted data.
-   [ ] Existing booking, payment, seat-hold, cancellation, and refund
    behavior remains unchanged unless separately approved.
-   [ ] Existing code and database structures have been audited before
    selecting the physical implementation.

## 16. Implementation guardrails

This specification authorizes requirements and design work only. It does
not authorize direct code changes.

Before implementation: 1. Audit the existing route, schedule, bus,
booking, and fare entities and APIs. 2. Map each requirement to existing
capabilities or explicitly identified gaps. 3. Separate UI-only work
from backend/data-model enhancements. 4. Define migrations, backward
compatibility, and rollback strategy for any schema changes. 5. Plan
segment fares and origin--destination overrides as a separate
enhancement if not already supported. 6. Preserve existing booking,
payment, seat-hold, cancellation, refund, and historical-data behavior.
7. Obtain explicit approval for implementation scope before modifying
the codebase.

------------------------------------------------------------------------

## 17. Open details to settle during technical design

The following details were not fully specified by the confirmed product
decisions and must be resolved before implementation:

1.  Whether operators select locations from a controlled catalog or can
    create new location records.
2.  Whether origin stops may offer dropping points and destination stops
    may offer boarding points.
3.  Whether route codes are unique globally or per operator.
4.  Whether route drafts are supported.
5.  What schedule states count as unresolved future dependencies for
    blocking structural edits.
6.  Whether boarding/drop-off times are distinct from stop
    arrival/departure times.
7.  The scope, validity, and precedence of manual fare overrides.
8.  Fare currency, precision, rounding, and minimum/maximum validation.
9.  Whether route history is preserved through versioned routes or
    schedule-specific stop snapshots.

These are implementation decisions; they must not be silently assumed or
used to change existing business behavior.

package com.traveleasy.booking.validator;

import com.traveleasy.booking.dto.PrimaryGuestDto;
import com.traveleasy.booking.dto.ReservationAllocationItemDto;
import com.traveleasy.booking.dto.RoomGuestAllocationDto;
import com.traveleasy.booking.dto.RoomOccupantDto;
import com.traveleasy.booking.model.GuestType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Reusable backend validator for structured guest allocation rules (WP-HB-06.1).
 * Validates primary guest information, physical room sequencing, adult/child occupancy,
 * child age constraints (0-17), and occupant records.
 */
@Component
public class HotelGuestAllocationValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-().]{7,25}$");

    /**
     * Validates primary guest contact information.
     */
    public void validatePrimaryGuest(PrimaryGuestDto primaryGuest) {
        List<String> errors = new ArrayList<>();
        collectPrimaryGuestErrors(primaryGuest, errors);
        if (!errors.isEmpty()) {
            throw new GuestAllocationValidationException(errors);
        }
    }

    /**
     * Validates an entire list of allocations with an optional capacity map by roomId.
     */
    public void validateAllocations(List<ReservationAllocationItemDto> allocations, Map<Long, Integer> capacityMap) {
        if (allocations == null || allocations.isEmpty()) {
            throw new GuestAllocationValidationException("allocations", "Allocations list cannot be null or empty");
        }

        List<String> errors = new ArrayList<>();
        for (int i = 0; i < allocations.size(); i++) {
            ReservationAllocationItemDto item = allocations.get(i);
            Integer capacity = (capacityMap != null && item != null && item.getRoomId() != null)
                    ? capacityMap.get(item.getRoomId())
                    : null;
            collectAllocationErrors(item, capacity, "allocations[" + i + "]", errors);
        }

        if (!errors.isEmpty()) {
            throw new GuestAllocationValidationException(errors);
        }
    }

    /**
     * Validates a single room allocation against a specific maxOccupancy capacity limit.
     */
    public void validateRoomAllocation(ReservationAllocationItemDto allocation, Integer maxOccupancy) {
        List<String> errors = new ArrayList<>();
        collectAllocationErrors(allocation, maxOccupancy, "allocation", errors);
        if (!errors.isEmpty()) {
            throw new GuestAllocationValidationException(errors);
        }
    }

    /**
     * Internal collector for primary guest errors.
     */
    public void collectPrimaryGuestErrors(PrimaryGuestDto primaryGuest, List<String> errors) {
        if (primaryGuest == null) {
            errors.add("primaryGuest: Primary guest information is mandatory");
            return;
        }

        if (primaryGuest.getFullName() == null || primaryGuest.getFullName().trim().isEmpty()) {
            errors.add("primaryGuest.fullName: Full name is mandatory and cannot be blank");
        }

        if (primaryGuest.getEmail() == null || primaryGuest.getEmail().trim().isEmpty()) {
            errors.add("primaryGuest.email: Email is mandatory and cannot be blank");
        } else if (!EMAIL_PATTERN.matcher(primaryGuest.getEmail().trim()).matches()) {
            errors.add("primaryGuest.email: Email format is invalid: " + primaryGuest.getEmail());
        }

        if (primaryGuest.getMobile() == null || primaryGuest.getMobile().trim().isEmpty()) {
            errors.add("primaryGuest.mobile: Mobile number is mandatory and cannot be blank");
        } else {
            String raw = primaryGuest.getMobile().trim();
            String digits = raw.replaceAll("[^0-9]", "");
            if (!MOBILE_PATTERN.matcher(raw).matches() || digits.length() < 7 || digits.length() > 15) {
                errors.add("primaryGuest.mobile: Mobile number format is invalid: " + primaryGuest.getMobile());
            }
        }
    }

    /**
     * Internal collector for allocation errors.
     */
    public void collectAllocationErrors(ReservationAllocationItemDto allocation, Integer maxOccupancy, String path, List<String> errors) {
        if (allocation == null) {
            errors.add(path + ": Allocation cannot be null");
            return;
        }

        if (allocation.getRoomId() == null || allocation.getRoomId() <= 0) {
            errors.add(path + ".roomId: Room ID must be a valid positive identifier");
        }

        if (allocation.getQuantity() == null || allocation.getQuantity() < 1) {
            errors.add(path + ".quantity: Quantity must be at least 1");
            return;
        }

        List<RoomGuestAllocationDto> rooms = allocation.getRooms();
        if (rooms == null || rooms.isEmpty()) {
            errors.add(path + ".rooms: Physical room allocation entries are required for quantity " + allocation.getQuantity());
            return;
        }

        if (rooms.size() != allocation.getQuantity()) {
            errors.add(path + ".rooms: Number of physical-room entries (" + rooms.size() + ") must equal allocation quantity (" + allocation.getQuantity() + ")");
        }

        Set<Integer> seenSequences = new HashSet<>();
        for (int rIdx = 0; rIdx < rooms.size(); rIdx++) {
            RoomGuestAllocationDto room = rooms.get(rIdx);
            String roomPath = path + ".rooms[" + rIdx + "]";

            if (room == null) {
                errors.add(roomPath + ": Room allocation entry cannot be null");
                continue;
            }

            // Room sequence validation
            if (room.getRoomSequence() == null || room.getRoomSequence() < 1) {
                errors.add(roomPath + ".roomSequence: Room sequence must be a positive integer");
            } else if (!seenSequences.add(room.getRoomSequence())) {
                errors.add(roomPath + ".roomSequence: Duplicate room sequence " + room.getRoomSequence() + " within allocation");
            }

            // Occupancy counts validation
            Integer adults = room.getAdults();
            Integer children = room.getChildren();

            if (adults == null || adults < 0) {
                errors.add(roomPath + ".adults: Adult count must be a non-negative integer");
            } else if (adults < 1) {
                errors.add(roomPath + ".adults: At least 1 adult is required per physical room");
            }
            if (children == null || children < 0) {
                errors.add(roomPath + ".children: Child count must be a non-negative integer");
            }

            int adultCount = (adults != null && adults >= 0) ? adults : 0;
            int childCount = (children != null && children >= 0) ? children : 0;
            int totalOccupants = adultCount + childCount;

            if (totalOccupants < 1) {
                errors.add(roomPath + ": Total occupants (adults + children) must be at least 1");
            }

            if (maxOccupancy != null && totalOccupants > maxOccupancy) {
                errors.add(roomPath + ": Total occupants (" + totalOccupants + ") exceeds max occupancy limit (" + maxOccupancy + ") for room " + allocation.getRoomId());
            }

            // Occupants list consistency validation
            List<RoomOccupantDto> occupants = room.getOccupants();
            if (occupants == null || occupants.isEmpty()) {
                if (totalOccupants > 0) {
                    errors.add(roomPath + ".occupants: Occupants list is required when occupancy is " + totalOccupants);
                }
                continue;
            }

            if (occupants.size() != totalOccupants) {
                errors.add(roomPath + ".occupants: Total occupant records (" + occupants.size() + ") does not equal declared total occupancy (" + totalOccupants + ")");
            }

            long actualAdults = occupants.stream().filter(o -> o != null && o.getType() == GuestType.ADULT).count();
            if (adults != null && adults >= 0 && actualAdults != adultCount) {
                errors.add(roomPath + ".occupants: Declared adult count (" + adultCount + ") does not match number of ADULT records (" + actualAdults + ")");
            }

            long actualChildren = occupants.stream().filter(o -> o != null && o.getType() == GuestType.CHILD).count();
            if (children != null && children >= 0 && actualChildren != childCount) {
                errors.add(roomPath + ".occupants: Declared child count (" + childCount + ") does not match number of CHILD records (" + actualChildren + ")");
            }

            // Individual occupant validation
            for (int oIdx = 0; oIdx < occupants.size(); oIdx++) {
                RoomOccupantDto occ = occupants.get(oIdx);
                String occPath = roomPath + ".occupants[" + oIdx + "]";

                if (occ == null) {
                    errors.add(occPath + ": Occupant entry cannot be null");
                    continue;
                }

                if (occ.getName() == null || occ.getName().trim().isEmpty()) {
                    errors.add(occPath + ".name: Occupant name is mandatory and cannot be blank");
                }

                if (occ.getType() == null) {
                    errors.add(occPath + ".type: Guest type is mandatory (ADULT or CHILD)");
                } else if (occ.getType() == GuestType.CHILD) {
                    if (occ.getAge() == null) {
                        errors.add(occPath + ".age: Child age is mandatory for child occupants");
                    } else if (occ.getAge() < 0 || occ.getAge() > 17) {
                        errors.add(occPath + ".age: Child age (" + occ.getAge() + ") must be an integer between 0 and 17 inclusive");
                    }
                } else if (occ.getType() == GuestType.ADULT) {
                    if (occ.getAge() != null) {
                        errors.add(occPath + ".age: Adult age should not be specified (must be null)");
                    }
                }
            }
        }

        for (int seq = 1; seq <= rooms.size(); seq++) {
            if (!seenSequences.contains(seq)) {
                errors.add(path + ".rooms: Missing room sequence " + seq + "; room sequences must be consecutive starting from 1");
                break;
            }
        }
    }
}

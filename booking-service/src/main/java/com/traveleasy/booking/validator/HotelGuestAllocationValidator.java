package com.traveleasy.booking.validator;

import com.traveleasy.booking.dto.PrimaryGuestDto;
import com.traveleasy.booking.dto.ReservationAllocationItemDto;
import com.traveleasy.booking.dto.RoomGuestAllocationDto;
import com.traveleasy.booking.dto.RoomOccupantDto;
import com.traveleasy.booking.model.GuestType;
import com.traveleasy.booking.validator.GuestAllocationValidationException.ValidationErrorDetail;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Reusable backend validator for structured guest allocation rules (WP-HB-06.1, extended WP-HB-UI-03).
 * Validates primary guest information, physical room sequencing, adult/child occupancy,
 * adult age (>= 18) and gender requirements, child age constraints (0-17) with null gender,
 * and declared count consistency.
 */
@Component
public class HotelGuestAllocationValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-().]{7,25}$");
    private static final int MAX_NAME_LENGTH = 255;

    /**
     * Validates primary guest contact information.
     */
    public void validatePrimaryGuest(PrimaryGuestDto primaryGuest) {
        List<String> errors = new ArrayList<>();
        List<ValidationErrorDetail> details = new ArrayList<>();
        collectPrimaryGuestErrors(primaryGuest, errors, details);
        if (!errors.isEmpty()) {
            throw new GuestAllocationValidationException(errors, details);
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
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (int i = 0; i < allocations.size(); i++) {
            ReservationAllocationItemDto item = allocations.get(i);
            Integer capacity = (capacityMap != null && item != null && item.getRoomId() != null)
                    ? capacityMap.get(item.getRoomId())
                    : null;
            collectAllocationErrors(item, capacity, i, "allocations[" + i + "]", errors, details);
        }

        if (!errors.isEmpty()) {
            throw new GuestAllocationValidationException(errors, details);
        }
    }

    /**
     * Validates a single room allocation against a specific maxOccupancy capacity limit.
     */
    public void validateRoomAllocation(ReservationAllocationItemDto allocation, Integer maxOccupancy) {
        List<String> errors = new ArrayList<>();
        List<ValidationErrorDetail> details = new ArrayList<>();
        collectAllocationErrors(allocation, maxOccupancy, 0, "allocation", errors, details);
        if (!errors.isEmpty()) {
            throw new GuestAllocationValidationException(errors, details);
        }
    }

    /**
     * Internal collector for primary guest errors.
     */
    public void collectPrimaryGuestErrors(PrimaryGuestDto primaryGuest, List<String> errors) {
        collectPrimaryGuestErrors(primaryGuest, errors, new ArrayList<>());
    }

    private void collectPrimaryGuestErrors(PrimaryGuestDto primaryGuest, List<String> errors, List<ValidationErrorDetail> details) {
        if (primaryGuest == null) {
            addError(errors, details, null, null, null, null, "primaryGuest", "Primary guest information is mandatory");
            return;
        }

        if (primaryGuest.getFullName() == null || primaryGuest.getFullName().trim().isEmpty()) {
            addError(errors, details, null, null, null, null, "primaryGuest.fullName", "Full name is mandatory and cannot be blank");
        }

        if (primaryGuest.getEmail() == null || primaryGuest.getEmail().trim().isEmpty()) {
            addError(errors, details, null, null, null, null, "primaryGuest.email", "Email is mandatory and cannot be blank");
        } else if (!EMAIL_PATTERN.matcher(primaryGuest.getEmail().trim()).matches()) {
            addError(errors, details, null, null, null, null, "primaryGuest.email", "Email format is invalid: " + primaryGuest.getEmail());
        }

        if (primaryGuest.getMobile() == null || primaryGuest.getMobile().trim().isEmpty()) {
            addError(errors, details, null, null, null, null, "primaryGuest.mobile", "Mobile number is mandatory and cannot be blank");
        } else {
            String raw = primaryGuest.getMobile().trim();
            String digits = raw.replaceAll("[^0-9]", "");
            if (!MOBILE_PATTERN.matcher(raw).matches() || digits.length() < 7 || digits.length() > 15) {
                addError(errors, details, null, null, null, null, "primaryGuest.mobile", "Mobile number format is invalid: " + primaryGuest.getMobile());
            }
        }
    }

    /**
     * Internal collector for allocation errors (kept signature-compatible).
     */
    public void collectAllocationErrors(ReservationAllocationItemDto allocation, Integer maxOccupancy, String path, List<String> errors) {
        collectAllocationErrors(allocation, maxOccupancy, null, path, errors, new ArrayList<>());
    }

    private void collectAllocationErrors(
            ReservationAllocationItemDto allocation,
            Integer maxOccupancy,
            Integer allocationIndex,
            String path,
            List<String> errors,
            List<ValidationErrorDetail> details) {

        if (allocation == null) {
            addError(errors, details, allocationIndex, null, null, null, path, "Allocation cannot be null");
            return;
        }

        Long roomId = allocation.getRoomId();
        if (roomId == null || roomId <= 0) {
            addError(errors, details, allocationIndex, roomId, null, null, path + ".roomId", "Room ID must be a valid positive identifier");
        }

        if (allocation.getQuantity() == null || allocation.getQuantity() < 1) {
            addError(errors, details, allocationIndex, roomId, null, null, path + ".quantity", "Quantity must be at least 1");
            return;
        }

        List<RoomGuestAllocationDto> rooms = allocation.getRooms();
        if (rooms == null || rooms.isEmpty()) {
            addError(errors, details, allocationIndex, roomId, null, null, path + ".rooms",
                    "Physical room allocation entries are required for quantity " + allocation.getQuantity());
            return;
        }

        if (rooms.size() != allocation.getQuantity()) {
            addError(errors, details, allocationIndex, roomId, null, null, path + ".rooms",
                    "Number of physical-room entries (" + rooms.size() + ") must equal allocation quantity (" + allocation.getQuantity() + ")");
        }

        Set<Integer> seenSequences = new HashSet<>();
        for (int rIdx = 0; rIdx < rooms.size(); rIdx++) {
            RoomGuestAllocationDto room = rooms.get(rIdx);
            String roomPath = path + ".rooms[" + rIdx + "]";

            if (room == null) {
                addError(errors, details, allocationIndex, roomId, rIdx + 1, null, roomPath, "Room allocation entry cannot be null");
                continue;
            }

            Integer seq = room.getRoomSequence();
            if (seq == null || seq < 1) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".roomSequence", "Room sequence must be a positive integer");
            } else if (!seenSequences.add(seq)) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".roomSequence",
                        "Duplicate room sequence " + seq + " within allocation");
            }

            Integer adults = room.getAdults();
            Integer children = room.getChildren();

            if (adults == null || adults < 0) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".adults", "Adult count must be a non-negative integer");
            } else if (adults < 1) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".adults", "At least 1 adult is required per physical room");
            }
            if (children == null || children < 0) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".children", "Child count must be a non-negative integer");
            }

            int adultCount = (adults != null && adults >= 0) ? adults : 0;
            int childCount = (children != null && children >= 0) ? children : 0;
            int totalOccupants = adultCount + childCount;

            if (totalOccupants < 1) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath, "Total occupants (adults + children) must be at least 1");
            }

            if (maxOccupancy != null && totalOccupants > maxOccupancy) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath,
                        "Total occupants (" + totalOccupants + ") exceeds max occupancy limit (" + maxOccupancy + ") for room " + roomId);
            }

            List<RoomOccupantDto> occupants = room.getOccupants();
            if (occupants == null || occupants.isEmpty()) {
                if (totalOccupants > 0) {
                    addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".occupants",
                            "Occupants list is required when occupancy is " + totalOccupants);
                }
                continue;
            }

            if (occupants.size() != totalOccupants) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".occupants",
                        "Total occupant records (" + occupants.size() + ") does not equal declared total occupancy (" + totalOccupants + ")");
            }

            long actualAdults = occupants.stream().filter(o -> o != null && o.getType() == GuestType.ADULT).count();
            if (adults != null && adults >= 0 && actualAdults != adultCount) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".occupants",
                        "Declared adult count (" + adultCount + ") does not match number of ADULT records (" + actualAdults + ")");
            }

            long actualChildren = occupants.stream().filter(o -> o != null && o.getType() == GuestType.CHILD).count();
            if (children != null && children >= 0 && actualChildren != childCount) {
                addError(errors, details, allocationIndex, roomId, seq, null, roomPath + ".occupants",
                        "Declared child count (" + childCount + ") does not match number of CHILD records (" + actualChildren + ")");
            }

            for (int oIdx = 0; oIdx < occupants.size(); oIdx++) {
                RoomOccupantDto occ = occupants.get(oIdx);
                String occPath = roomPath + ".occupants[" + oIdx + "]";

                if (occ == null) {
                    addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath, "Occupant entry cannot be null");
                    continue;
                }

                if (occ.getName() == null || occ.getName().trim().isEmpty()) {
                    addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".name", "Occupant name is mandatory and cannot be blank");
                } else if (occ.getName().trim().length() > MAX_NAME_LENGTH) {
                    addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".name",
                            "Occupant name cannot exceed " + MAX_NAME_LENGTH + " characters");
                }

                if (occ.getType() == null) {
                    addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".type", "Guest type is mandatory (ADULT or CHILD)");
                } else if (occ.getType() == GuestType.CHILD) {
                    if (occ.getAge() == null) {
                        addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".age", "Child age is mandatory for child occupants");
                    } else if (occ.getAge() < 0 || occ.getAge() > 17) {
                        addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".age",
                                "Child age (" + occ.getAge() + ") must be an integer between 0 and 17 inclusive");
                    }
                    if (occ.getGender() != null) {
                        addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".gender",
                                "Gender must not be populated (must be null) for child occupants");
                    }
                } else if (occ.getType() == GuestType.ADULT) {
                    if (occ.getAge() == null) {
                        addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".age", "Adult age is mandatory for adult occupants");
                    } else if (occ.getAge() < 18) {
                        addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".age",
                                "Adult age (" + occ.getAge() + ") must be at least 18");
                    }
                    if (occ.getGender() == null) {
                        addError(errors, details, allocationIndex, roomId, seq, oIdx, occPath + ".gender", "Adult gender is mandatory for adult occupants");
                    }
                }
            }
        }

        for (int seq = 1; seq <= rooms.size(); seq++) {
            if (!seenSequences.contains(seq)) {
                addError(errors, details, allocationIndex, roomId, seq, null, path + ".rooms",
                        "Missing room sequence " + seq + "; room sequences must be consecutive starting from 1");
                break;
            }
        }
    }

    private void addError(
            List<String> errors,
            List<ValidationErrorDetail> details,
            Integer allocationIndex,
            Long roomId,
            Integer roomSequence,
            Integer guestIndex,
            String fieldPath,
            String message) {
        errors.add(fieldPath + ": " + message);
        details.add(new ValidationErrorDetail(allocationIndex, roomId, roomSequence, guestIndex, fieldPath, message));
    }
}

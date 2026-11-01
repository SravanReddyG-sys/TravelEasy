package com.traveleasy.bus.service;

import com.traveleasy.bus.model.Bus;
import com.traveleasy.bus.repository.BusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusValidationService {

    private final BusRepository busRepository;

    public static final Pattern STANDARD_REGISTRATION_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$");
    public static final Pattern BH_SERIES_REGISTRATION_PATTERN = Pattern.compile("^[0-9]{2}BH[0-9]{4}[A-Z]{1,2}$");
    public static final Pattern CHASSIS_NUMBER_PATTERN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    public static final Pattern ENGINE_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9/-]{5,25}$");

    public static final Set<String> ALLOWED_MANUFACTURERS = Set.of(
            "volvo", "scania", "ashok leyland", "tata motors", "eicher", "bharatbenz", "mercedes-benz", "force motors"
    );

    public static final Set<String> ALLOWED_BUS_TYPES = Set.of(
            "seater", "sleeper", "seater cum sleeper"
    );

    public static final Set<String> ALLOWED_FUEL_TYPES = Set.of(
            "diesel", "cng", "electric", "petrol", "lng", "hybrid"
    );

    public static final Set<String> ALLOWED_EMISSION_STANDARDS = Set.of(
            "bs iii", "bs iv", "bs vi", "bs vi phase 2", "other"
    );

    public static final Set<String> ALLOWED_COLORS = Set.of(
            "white", "blue", "white with blue", "red", "silver", "grey", "gray", "yellow", "green", "black", "other"
    );

    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "stage carriage", "stage carriage (contract)", "contract carriage", "tourist", "private service"
    );

    /**
     * Normalizes a bus registration number by removing spaces, hyphens, and converting to uppercase.
     */
    public String normalizeRegistrationNumber(String regNo) {
        if (regNo == null) return null;
        return regNo.replaceAll("[\\s-]+", "").toUpperCase().trim();
    }

    /**
     * Checks if a registration number is valid (Standard Indian or BH-series).
     */
    public boolean isValidRegistrationNumber(String regNo) {
        String normalized = normalizeRegistrationNumber(regNo);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return STANDARD_REGISTRATION_PATTERN.matcher(normalized).matches()
                || BH_SERIES_REGISTRATION_PATTERN.matcher(normalized).matches();
    }

    /**
     * Checks if a bus number is already registered in the system.
     */
    public boolean isRegistrationNumberTaken(String regNo, Long excludeBusId) {
        String normalized = normalizeRegistrationNumber(regNo);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        Optional<Bus> existing = busRepository.findByBusNumber(normalized);
        if (existing.isEmpty()) {
            return false;
        }
        return excludeBusId == null || !existing.get().getId().equals(excludeBusId);
    }

    /**
     * Validates Step 1 Basic Details.
     * Throws IllegalArgumentException on the first violation, or returns a map of field errors.
     */
    public Map<String, String> validateBasicDetails(Bus bus, boolean checkImagePresence) {
        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Registration Number
        if (bus.getBusNumber() == null || bus.getBusNumber().trim().isEmpty()) {
            errors.put("busNumber", "Registration Number is required.");
        } else {
            String normalized = normalizeRegistrationNumber(bus.getBusNumber());
            if (!isValidRegistrationNumber(normalized)) {
                errors.put("busNumber", "Invalid registration number format. Standard (e.g., KA01AB1234) or BH-series (e.g., 22BH1234AA) expected.");
            } else if (isRegistrationNumberTaken(normalized, bus.getId())) {
                errors.put("busNumber", "Registration Number '" + normalized + "' is already registered in the system.");
            }
        }

        // 2. Bus Name (Optional, max 50 chars)
        if (bus.getBusName() != null && bus.getBusName().trim().length() > 50) {
            errors.put("busName", "Bus Name cannot exceed 50 characters.");
        }

        // 3. Manufacturer (Required, predefined catalog)
        if (bus.getManufacturer() == null || bus.getManufacturer().trim().isEmpty()) {
            errors.put("manufacturer", "Manufacturer is required.");
        } else if (!ALLOWED_MANUFACTURERS.contains(bus.getManufacturer().trim().toLowerCase())) {
            errors.put("manufacturer", "Manufacturer '" + bus.getManufacturer() + "' is not supported. Please select from the approved catalog.");
        }

        // 4. Model (Required, 2-60 chars)
        if (bus.getModel() == null || bus.getModel().trim().isEmpty()) {
            errors.put("model", "Model name is required.");
        } else if (bus.getModel().trim().length() < 2 || bus.getModel().trim().length() > 60) {
            errors.put("model", "Model name must be between 2 and 60 characters.");
        }

        // 5. Bus Type (Required)
        if (bus.getBusType() == null || bus.getBusType().trim().isEmpty()) {
            errors.put("busType", "Bus Type is required.");
        } else if (!ALLOWED_BUS_TYPES.contains(bus.getBusType().trim().toLowerCase())) {
            errors.put("busType", "Invalid Bus Type. Allowed types are: Seater, Sleeper, Seater Cum Sleeper.");
        }

        // 6. Fuel Type (Required)
        if (bus.getFuelType() == null || bus.getFuelType().trim().isEmpty()) {
            errors.put("fuelType", "Fuel Type is required.");
        } else if (!ALLOWED_FUEL_TYPES.contains(bus.getFuelType().trim().toLowerCase())) {
            errors.put("fuelType", "Invalid Fuel Type. Allowed types are: Diesel, CNG, Electric, Petrol, LNG, Hybrid.");
        }

        // 7. Chassis Number (Required, 17 characters, no I, O, Q)
        if (bus.getChassisNumber() == null || bus.getChassisNumber().trim().isEmpty()) {
            errors.put("chassisNumber", "Chassis Number is required.");
        } else {
            String upperChassis = bus.getChassisNumber().trim().toUpperCase();
            if (!CHASSIS_NUMBER_PATTERN.matcher(upperChassis).matches()) {
                errors.put("chassisNumber", "Chassis Number must be exactly 17 alphanumeric characters (excluding letters I, O, and Q).");
            }
        }

        // 8. Year of Manufacturing (Required, 1980 - current year)
        int currentYear = Year.now().getValue();
        if (bus.getYearOfManufacturing() == null) {
            errors.put("yearOfManufacturing", "Year of Manufacturing is required.");
        } else if (bus.getYearOfManufacturing() < 1980 || bus.getYearOfManufacturing() > currentYear) {
            errors.put("yearOfManufacturing", "Year of Manufacturing must be between 1980 and " + currentYear + ".");
        }

        // 9. Engine Number (Required, 5-25 chars)
        if (bus.getEngineNumber() == null || bus.getEngineNumber().trim().isEmpty()) {
            errors.put("engineNumber", "Engine Number is required.");
        } else {
            String upperEngine = bus.getEngineNumber().trim().toUpperCase();
            if (!ENGINE_NUMBER_PATTERN.matcher(upperEngine).matches()) {
                errors.put("engineNumber", "Engine Number must be 5 to 25 characters (letters, numbers, hyphens, and slashes allowed).");
            }
        }

        // 10. Emission Standard (Required)
        if (bus.getEmissionStandard() == null || bus.getEmissionStandard().trim().isEmpty()) {
            errors.put("emissionStandard", "Emission Standard is required.");
        } else if (!ALLOWED_EMISSION_STANDARDS.contains(bus.getEmissionStandard().trim().toLowerCase())) {
            errors.put("emissionStandard", "Invalid Emission Standard. Allowed values: BS III, BS IV, BS VI, BS VI Phase 2, Other.");
        }

        // 11. Color (Required)
        if (bus.getColor() == null || bus.getColor().trim().isEmpty()) {
            errors.put("color", "Color is required.");
        } else if (!ALLOWED_COLORS.contains(bus.getColor().trim().toLowerCase())) {
            errors.put("color", "Invalid Color selection.");
        }

        // 12. Bus Category (Required)
        if (bus.getBusCategory() == null || bus.getBusCategory().trim().isEmpty()) {
            errors.put("busCategory", "Bus Category is required.");
        } else if (!ALLOWED_CATEGORIES.contains(bus.getBusCategory().trim().toLowerCase())) {
            errors.put("busCategory", "Invalid Bus Category.");
        }

        // 13. Air Conditioning (facilityType) (Required)
        if (bus.getFacilityType() == null || bus.getFacilityType().trim().isEmpty()) {
            errors.put("facilityType", "Air Conditioning selection is required (Yes / No).");
        } else {
            String fac = bus.getFacilityType().trim().toUpperCase();
            if (!fac.equals("AC") && !fac.equals("NON_AC") && !fac.equals("YES (AC)") && !fac.equals("NO (NON-AC)")) {
                errors.put("facilityType", "Invalid Air Conditioning selection.");
            }
        }

        // 14. Images (Required)
        if (checkImagePresence) {
            if (bus.getExteriorImageUrl() == null || bus.getExteriorImageUrl().trim().isEmpty()) {
                errors.put("exteriorImage", "Exterior image is required.");
            }
            if (bus.getInteriorImageUrl() == null || bus.getInteriorImageUrl().trim().isEmpty()) {
                errors.put("interiorImage", "Interior image is required.");
            }
        }

        return errors;
    }
}

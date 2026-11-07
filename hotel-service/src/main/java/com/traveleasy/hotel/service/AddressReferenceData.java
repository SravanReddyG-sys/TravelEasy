package com.traveleasy.hotel.service;

import java.util.*;
import java.util.regex.Pattern;

public class AddressReferenceData {

    public static final String DEFAULT_COUNTRY = "India";
    public static List<String> INDIAN_STATES_AND_UTS;

    // Regular expression for Indian 6-digit postal code (PIN Code)
    private static final Pattern INDIA_PIN_PATTERN = Pattern.compile("^[1-9][0-9]{5}$");

    // Broad pattern for international postal codes (3 to 10 alphanumeric characters, spaces, hyphens)
    private static final Pattern INTL_POSTAL_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s-]{3,10}$");

    private static final List<String> COUNTRIES = List.of(
            "India",
            "United States",
            "United Kingdom",
            "United Arab Emirates",
            "Australia",
            "Canada",
            "Singapore",
            "Germany",
            "France",
            "Japan",
            "Malaysia",
            "Thailand",
            "Saudi Arabia",
            "South Africa",
            "Italy",
            "Spain",
            "Netherlands",
            "Switzerland",
            "New Zealand"
    );

    private static final Map<String, List<String>> COUNTRY_STATES = new HashMap<>();

    static {
        // India (28 states + 8 Union Territories)
        COUNTRY_STATES.put("India", List.of(
                "Andhra Pradesh",
                "Arunachal Pradesh",
                "Assam",
                "Bihar",
                "Chhattisgarh",
                "Goa",
                "Gujarat",
                "Haryana",
                "Himachal Pradesh",
                "Jharkhand",
                "Karnataka",
                "Kerala",
                "Madhya Pradesh",
                "Maharashtra",
                "Manipur",
                "Meghalaya",
                "Mizoram",
                "Nagaland",
                "Odisha",
                "Punjab",
                "Rajasthan",
                "Sikkim",
                "Tamil Nadu",
                "Telangana",
                "Tripura",
                "Uttar Pradesh",
                "Uttarakhand",
                "West Bengal",
                "Andaman and Nicobar Islands",
                "Chandigarh",
                "Dadra and Nagar Haveli and Daman and Diu",
                "Delhi",
                "Jammu and Kashmir",
                "Ladakh",
                "Lakshadweep",
                "Puducherry"
        ));

        INDIAN_STATES_AND_UTS = COUNTRY_STATES.get("India");

        // United States (50 states + DC)
        COUNTRY_STATES.put("United States", List.of(
                "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut",
                "Delaware", "District of Columbia", "Florida", "Georgia", "Hawaii", "Idaho", "Illinois",
                "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts",
                "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada",
                "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota",
                "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina",
                "South Dakota", "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington",
                "West Virginia", "Wisconsin", "Wyoming"
        ));

        // United Kingdom
        COUNTRY_STATES.put("United Kingdom", List.of(
                "England", "Scotland", "Wales", "Northern Ireland"
        ));

        // United Arab Emirates
        COUNTRY_STATES.put("United Arab Emirates", List.of(
                "Abu Dhabi", "Dubai", "Sharjah", "Ajman", "Umm Al Quwain", "Ras Al Khaimah", "Fujairah"
        ));

        // Australia
        COUNTRY_STATES.put("Australia", List.of(
                "New South Wales", "Victoria", "Queensland", "Western Australia",
                "South Australia", "Tasmania", "Australian Capital Territory", "Northern Territory"
        ));

        // Canada
        COUNTRY_STATES.put("Canada", List.of(
                "Ontario", "Quebec", "British Columbia", "Alberta", "Manitoba", "Saskatchewan",
                "Nova Scotia", "New Brunswick", "Newfoundland and Labrador", "Prince Edward Island",
                "Northwest Territories", "Yukon", "Nunavut"
        ));

        // Singapore
        COUNTRY_STATES.put("Singapore", List.of(
                "Central Region", "East Region", "North Region", "North-East Region", "West Region"
        ));
    }

    public static List<String> getSupportedCountries() {
        return Collections.unmodifiableList(COUNTRIES);
    }

    public static boolean isCountrySupported(String country) {
        if (country == null) return false;
        String trimmed = country.trim();
        return COUNTRIES.stream().anyMatch(c -> c.equalsIgnoreCase(trimmed));
    }

    public static String normalizeCountry(String country) {
        if (country == null) return null;
        String trimmed = country.trim();
        for (String c : COUNTRIES) {
            if (c.equalsIgnoreCase(trimmed)) {
                return c;
            }
        }
        return trimmed;
    }

    public static List<String> getStatesForCountry(String country) {
        if (country == null) return Collections.emptyList();
        String normCountry = normalizeCountry(country);
        List<String> states = COUNTRY_STATES.get(normCountry);
        return states != null ? Collections.unmodifiableList(states) : Collections.emptyList();
    }

    public static boolean isStateValidForCountry(String country, String state) {
        if (country == null || state == null) return false;
        String normCountry = normalizeCountry(country);
        List<String> states = COUNTRY_STATES.get(normCountry);
        if (states == null || states.isEmpty()) {
            // For countries without a pre-defined state list, allow non-empty state
            return !state.trim().isEmpty() && state.trim().length() <= 100;
        }
        String trimmedState = state.trim();
        return states.stream().anyMatch(s -> s.equalsIgnoreCase(trimmedState));
    }

    public static String normalizeState(String country, String state) {
        if (country == null || state == null) return state;
        String normCountry = normalizeCountry(country);
        List<String> states = COUNTRY_STATES.get(normCountry);
        if (states != null) {
            String trimmedState = state.trim();
            for (String s : states) {
                if (s.equalsIgnoreCase(trimmedState)) {
                    return s;
                }
            }
        }
        return state.trim();
    }

    public static boolean isValidPostalCode(String country, String postalCode) {
        if (postalCode == null || postalCode.trim().isEmpty()) {
            return false;
        }
        String trimmed = postalCode.trim();
        String normCountry = normalizeCountry(country);
        if ("India".equalsIgnoreCase(normCountry)) {
            return INDIA_PIN_PATTERN.matcher(trimmed).matches();
        }
        return INTL_POSTAL_PATTERN.matcher(trimmed).matches();
    }
}

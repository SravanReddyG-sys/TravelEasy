package com.traveleasy.hotel.controller;

import com.traveleasy.hotel.model.HotelProperty;
import com.traveleasy.hotel.model.RoomType;
import com.traveleasy.hotel.repository.HotelPropertyRepository;
import com.traveleasy.hotel.repository.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelPropertyRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;

    @PostMapping
    public ResponseEntity<HotelProperty> addHotel(@RequestBody HotelProperty hotel) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelRepository.save(hotel));
    }

    @GetMapping
    public ResponseEntity<List<HotelProperty>> getAllHotels() {
        return ResponseEntity.ok(hotelRepository.findAll());
    }

    @GetMapping("/manager/{managerId}")
    public ResponseEntity<List<HotelProperty>> getHotelsByManager(@PathVariable Long managerId) {
        return ResponseEntity.ok(hotelRepository.findByManagerId(managerId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<HotelProperty>> searchHotels(
            @RequestParam String city,
            @RequestParam(required = false) String checkIn,
            @RequestParam(required = false) String checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer guests) {
        return ResponseEntity.ok(hotelRepository.findByCityIgnoreCase(city));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getHotelDetails(@PathVariable Long id) {
        HotelProperty hotel = hotelRepository.findById(id).orElse(null);
        if (hotel == null) {
            return ResponseEntity.notFound().build();
        }
        List<RoomType> rooms = roomTypeRepository.findByHotelId(id);
        return ResponseEntity.ok(Map.of("hotel", hotel, "rooms", rooms));
    }

    @PostMapping("/{hotelId}/rooms")
    public ResponseEntity<RoomType> addRoomType(@PathVariable Long hotelId, @RequestBody RoomType roomType) {
        roomType.setHotelId(hotelId);
        if (roomType.getAvailableRooms() == null) {
            roomType.setAvailableRooms(roomType.getTotalRooms());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(roomTypeRepository.save(roomType));
    }

    @PutMapping("/rooms/{roomId}/reserve")
    public ResponseEntity<?> reserveRooms(@PathVariable Long roomId, @RequestParam Integer count) {
        RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
        if (roomType == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room type not found"));
        }
        if (roomType.getAvailableRooms() < count) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insufficient room inventory available"));
        }
        roomType.setAvailableRooms(roomType.getAvailableRooms() - count);
        roomTypeRepository.save(roomType);
        return ResponseEntity.ok(Map.of("message", "Rooms reserved successfully", "roomType", roomType));
    }

    @PutMapping("/rooms/{roomId}/release")
    public ResponseEntity<?> releaseRooms(@PathVariable Long roomId, @RequestParam Integer count) {
        RoomType roomType = roomTypeRepository.findById(roomId).orElse(null);
        if (roomType == null) return ResponseEntity.badRequest().build();

        roomType.setAvailableRooms(roomType.getAvailableRooms() + count);
        roomTypeRepository.save(roomType);
        return ResponseEntity.ok(Map.of("message", "Rooms released successfully"));
    }
}

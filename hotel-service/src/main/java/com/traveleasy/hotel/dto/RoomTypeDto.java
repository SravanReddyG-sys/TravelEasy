package com.traveleasy.hotel.dto;

import com.traveleasy.hotel.model.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeDto {
    private Long id;
    private Long hotelId;
    private String roomTypeName;
    private Integer maxOccupancy;
    private Double pricePerNight;
    private Integer totalRooms;
    private Integer availableRooms;
    private Integer allocatedRooms;
    private String amenities;
    private String imageUrl;
    private String status;

    public static RoomTypeDto fromEntity(RoomType room) {
        if (room == null) return null;
        int total = room.getTotalRooms() != null ? room.getTotalRooms() : 0;
        int avail = room.getAvailableRooms() != null ? room.getAvailableRooms() : 0;
        int allocated = Math.max(0, total - avail);
        return RoomTypeDto.builder()
                .id(room.getId())
                .hotelId(room.getHotelId())
                .roomTypeName(room.getRoomTypeName())
                .maxOccupancy(room.getMaxOccupancy())
                .pricePerNight(room.getPricePerNight())
                .totalRooms(room.getTotalRooms())
                .availableRooms(room.getAvailableRooms())
                .allocatedRooms(allocated)
                .amenities(room.getAmenities())
                .imageUrl(room.getImageUrl())
                .status(room.getStatus() != null ? room.getStatus() : "ACTIVE")
                .build();
    }
}

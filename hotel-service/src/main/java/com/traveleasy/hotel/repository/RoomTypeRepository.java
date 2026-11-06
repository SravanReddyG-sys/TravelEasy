package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findByHotelId(Long hotelId);
    List<RoomType> findByHotelIdAndStatus(Long hotelId, String status);
    Optional<RoomType> findByIdAndHotelId(Long id, Long hotelId);
    Optional<RoomType> findByHotelIdAndRoomTypeNameIgnoreCase(Long hotelId, String roomTypeName);
    Optional<RoomType> findByHotelIdAndRoomTypeNameIgnoreCaseAndStatus(Long hotelId, String roomTypeName, String status);
}

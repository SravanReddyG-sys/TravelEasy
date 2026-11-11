package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.RoomInventoryBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomInventoryBlockRepository extends JpaRepository<RoomInventoryBlock, Long> {

    List<RoomInventoryBlock> findByHotelIdOrderByCreatedAtDesc(Long hotelId);

    List<RoomInventoryBlock> findByRoomIdOrderByCreatedAtDesc(Long roomId);

    List<RoomInventoryBlock> findByRoomIdAndStatus(Long roomId, String status);

    List<RoomInventoryBlock> findByHotelIdAndStatus(Long hotelId, String status);
}

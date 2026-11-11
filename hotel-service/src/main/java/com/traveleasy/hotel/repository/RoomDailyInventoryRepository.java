package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.RoomDailyInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomDailyInventoryRepository extends JpaRepository<RoomDailyInventory, Long> {

    Optional<RoomDailyInventory> findByRoomIdAndInventoryDate(Long roomId, LocalDate inventoryDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomDailyInventory r WHERE r.roomId = :roomId AND r.inventoryDate = :inventoryDate")
    Optional<RoomDailyInventory> findByRoomIdAndInventoryDateForUpdate(
            @Param("roomId") Long roomId,
            @Param("inventoryDate") LocalDate inventoryDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomDailyInventory r WHERE r.roomId = :roomId AND r.inventoryDate >= :startDate AND r.inventoryDate < :endDate ORDER BY r.inventoryDate ASC")
    List<RoomDailyInventory> findByRoomIdAndInventoryDateRangeForUpdate(
            @Param("roomId") Long roomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<RoomDailyInventory> findByRoomIdAndInventoryDateBetweenOrderByInventoryDateAsc(
            Long roomId, LocalDate startDate, LocalDate endDate);

    List<RoomDailyInventory> findByRoomIdAndInventoryDateGreaterThanEqualOrderByInventoryDateAsc(
            Long roomId, LocalDate startDate);

    List<RoomDailyInventory> findByHotelIdAndInventoryDateBetweenOrderByInventoryDateAsc(
            Long hotelId, LocalDate startDate, LocalDate endDate);

    @Modifying
    @Query(value = "INSERT INTO room_daily_inventory (hotel_id, room_id, inventory_date, total_rooms, booked_rooms, held_rooms, blocked_rooms) " +
            "VALUES (:hotelId, :roomId, :inventoryDate, :totalRooms, 0, 0, 0) " +
            "ON DUPLICATE KEY UPDATE id=id", nativeQuery = true)
    void insertIfNotExists(
            @Param("hotelId") Long hotelId,
            @Param("roomId") Long roomId,
            @Param("inventoryDate") LocalDate inventoryDate,
            @Param("totalRooms") Integer totalRooms);

}

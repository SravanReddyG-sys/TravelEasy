package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.RoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findByHotelId(Long hotelId);
    List<RoomType> findByHotelIdAndStatus(Long hotelId, String status);
    Optional<RoomType> findByIdAndHotelId(Long id, Long hotelId);
    Optional<RoomType> findByHotelIdAndRoomTypeNameIgnoreCase(Long hotelId, String roomTypeName);
    Optional<RoomType> findByHotelIdAndRoomTypeNameIgnoreCaseAndStatus(Long hotelId, String roomTypeName, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomType r WHERE r.id = :id")
    Optional<RoomType> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomType r WHERE r.id IN :ids ORDER BY r.id ASC")
    List<RoomType> findAllByIdInOrderByIdAscForUpdate(@Param("ids") Collection<Long> ids);
}

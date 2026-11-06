package com.traveleasy.hotel.repository;

import com.traveleasy.hotel.model.HotelImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelImageRepository extends JpaRepository<HotelImage, Long> {
    List<HotelImage> findByHotelIdOrderByDisplayOrderAsc(Long hotelId);
    Optional<HotelImage> findByHotelIdAndIsCoverTrue(Long hotelId);
    List<HotelImage> findByImageUrlContaining(String filename);
    void deleteByHotelId(Long hotelId);
    void deleteByHotelIdAndImageUrl(Long hotelId, String imageUrl);
}

package com.traveleasy.traveller.repository;

import com.traveleasy.traveller.model.TravellerFavourite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TravellerFavouriteRepository extends JpaRepository<TravellerFavourite, Long> {
    List<TravellerFavourite> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<TravellerFavourite> findByUserIdAndItemTypeOrderByCreatedAtDesc(Long userId, String itemType);
    Optional<TravellerFavourite> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndItemTypeAndItemId(Long userId, String itemType, Long itemId);
    long countByUserId(Long userId);
}

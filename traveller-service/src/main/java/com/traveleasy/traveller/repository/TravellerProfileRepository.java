package com.traveleasy.traveller.repository;

import com.traveleasy.traveller.model.TravellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TravellerProfileRepository extends JpaRepository<TravellerProfile, Long> {
    Optional<TravellerProfile> findByUserId(Long userId);
}

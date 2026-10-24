package com.traveleasy.traveller.repository;

import com.traveleasy.traveller.model.TravellerPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TravellerPreferenceRepository extends JpaRepository<TravellerPreference, Long> {
    Optional<TravellerPreference> findByUserId(Long userId);
}

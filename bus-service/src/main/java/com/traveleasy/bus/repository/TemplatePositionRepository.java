package com.traveleasy.bus.repository;

import com.traveleasy.bus.model.TemplatePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplatePositionRepository extends JpaRepository<TemplatePosition, Long> {
    List<TemplatePosition> findByTemplate_IdOrderByVisualOrderAsc(String templateId);
    Optional<TemplatePosition> findByTemplate_IdAndPositionId(String templateId, String positionId);
    long countByTemplate_Id(String templateId);
}

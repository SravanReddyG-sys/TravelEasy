package com.traveleasy.notification.repository;

import com.traveleasy.notification.model.ReceivedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ReceivedEventRepository extends JpaRepository<ReceivedEvent, Long> {
    Optional<ReceivedEvent> findByEventId(String eventId);
    boolean existsByEventId(String eventId);

    @Transactional
    int deleteByReceivedAtBefore(Instant threshold);
}

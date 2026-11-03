package com.traveleasy.notification.repository;

import com.traveleasy.notification.model.InAppNotification;
import com.traveleasy.notification.model.RecipientRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findByRecipientUserIdAndRecipientRoleOrderByCreatedAtDesc(
            Long recipientUserId, RecipientRole recipientRole);

    long countByRecipientUserIdAndRecipientRoleAndIsReadFalse(
            Long recipientUserId, RecipientRole recipientRole);

    Optional<InAppNotification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    List<InAppNotification> findByEventId(String eventId);

    @Transactional
    @Modifying
    @Query("UPDATE InAppNotification n SET n.isRead = true, n.readAt = :readAt " +
           "WHERE n.recipientUserId = :userId AND n.recipientRole = :role AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") Long userId,
                             @Param("role") RecipientRole role,
                             @Param("readAt") Instant readAt);

    @Transactional
    int deleteByExpiresAtBefore(Instant threshold);
}

package com.traveleasy.notification.repository;

import com.traveleasy.notification.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByRecipientEmailOrderByTimestampDesc(String recipientEmail);
}

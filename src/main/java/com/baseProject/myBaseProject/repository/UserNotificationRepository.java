package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Page<UserNotification> findByUserId(Long userId, Pageable pageable);

    Page<UserNotification> findByUserIdAndReadAtIsNull(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("""
            UPDATE UserNotification notification
            SET notification.readAt = :readAt
            WHERE notification.id = :id
              AND notification.user.id = :userId
              AND notification.readAt IS NULL
            """)
    int markRead(@Param("userId") Long userId,
                 @Param("id") Long id,
                 @Param("readAt") Instant readAt);

    @Modifying
    @Query("""
            UPDATE UserNotification notification
            SET notification.readAt = :readAt
            WHERE notification.user.id = :userId
              AND notification.readAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    boolean existsByIdAndUserId(Long id, Long userId);
}

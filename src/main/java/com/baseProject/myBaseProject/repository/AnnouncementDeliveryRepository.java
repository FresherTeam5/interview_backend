package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.AnnouncementDelivery;
import com.baseProject.myBaseProject.enums.AnnouncementDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnouncementDeliveryRepository
        extends JpaRepository<AnnouncementDelivery, Long> {
    List<AnnouncementDelivery> findTop100ByAnnouncementIdAndStatusInAndAttemptsLessThanOrderById(
            Long announcementId,
            Collection<AnnouncementDeliveryStatus> statuses,
            int attempts);

    long countByAnnouncementId(Long announcementId);

    long countByAnnouncementIdAndStatus(Long announcementId, AnnouncementDeliveryStatus status);

    long countByAnnouncementIdAndStatusInAndAttemptsLessThan(
            Long announcementId,
            Collection<AnnouncementDeliveryStatus> statuses,
            int attempts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT delivery FROM AnnouncementDelivery delivery WHERE delivery.id = :id")
    Optional<AnnouncementDelivery> findByIdForUpdate(@Param("id") Long id);
}

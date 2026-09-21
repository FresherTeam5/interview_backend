package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.AdminAnnouncement;
import com.baseProject.myBaseProject.enums.AnnouncementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminAnnouncementRepository extends JpaRepository<AdminAnnouncement, Long> {
    Page<AdminAnnouncement> findByStatus(AnnouncementStatus status, Pageable pageable);

    List<AdminAnnouncement> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            AnnouncementStatus status, Instant now);

    List<AdminAnnouncement> findTop20ByStatusInOrderByStartedAtAsc(
            Collection<AnnouncementStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT announcement FROM AdminAnnouncement announcement WHERE announcement.id = :id")
    Optional<AdminAnnouncement> findByIdForUpdate(@Param("id") Long id);
}

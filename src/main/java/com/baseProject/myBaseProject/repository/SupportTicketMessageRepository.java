package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SupportTicketMessage;
import com.baseProject.myBaseProject.enums.SupportMessageVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {
    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);

    List<SupportTicketMessage> findByTicketIdAndVisibilityOrderByCreatedAtAscIdAsc(
            Long ticketId, SupportMessageVisibility visibility);
}

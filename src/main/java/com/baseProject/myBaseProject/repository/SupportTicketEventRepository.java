package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SupportTicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketEventRepository extends JpaRepository<SupportTicketEvent, Long> {
    List<SupportTicketEvent> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}

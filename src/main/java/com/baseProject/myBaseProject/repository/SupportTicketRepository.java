package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    @EntityGraph(attributePaths = {"session", "turn"})
    Page<SupportTicket> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"session", "turn"})
    Optional<SupportTicket> findByIdAndUserId(Long id, Long userId);
}

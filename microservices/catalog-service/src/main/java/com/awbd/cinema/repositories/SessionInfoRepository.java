package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.SessionInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionInfoRepository extends JpaRepository<SessionInfo, Long> {
}

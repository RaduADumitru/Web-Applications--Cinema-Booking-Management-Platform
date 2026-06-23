package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.SagaInstance;
import com.awbd.cinema.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    List<SagaInstance> findByStatus(SagaStatus status);
}

package com.awbd.cinema.entities;

import com.awbd.cinema.enums.SagaStatus;
import com.awbd.cinema.enums.SagaType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SagaInstance {

    @Id
    @Column(name = "saga_id")
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_type", nullable = false)
    private SagaType sagaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SagaStatus status;

    // Index of the last step that completed successfully; -1 means none yet.
    @Column(name = "last_completed_step", nullable = false)
    private int lastCompletedStep;

    // JSON snapshot of saga-critical identifiers (userId, orderId, pointsBefore, etc.)
    // for crash-recovery inspection; not used by runtime logic.
    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

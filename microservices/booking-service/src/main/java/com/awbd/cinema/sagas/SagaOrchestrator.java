package com.awbd.cinema.sagas;

import com.awbd.cinema.entities.SagaInstance;
import com.awbd.cinema.enums.SagaStatus;
import com.awbd.cinema.enums.SagaType;
import com.awbd.cinema.repositories.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public abstract class SagaOrchestrator<C, R> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final SagaInstanceRepository sagaInstanceRepository;

    protected SagaOrchestrator(SagaInstanceRepository sagaInstanceRepository) {
        this.sagaInstanceRepository = sagaInstanceRepository;
    }

    protected abstract List<SagaStep<C>> steps();
    protected abstract SagaType sagaType();
    protected abstract UUID sagaId(C context);
    protected abstract String serializePayload(C context);
    protected abstract R result(C context);

    public R execute(C context) {
        SagaInstance saga = sagaInstanceRepository.save(SagaInstance.builder()
                .sagaId(sagaId(context))
                .sagaType(sagaType())
                .status(SagaStatus.STARTED)
                .lastCompletedStep(-1)
                .payload(serializePayload(context))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        List<SagaStep<C>> steps = steps();
        int lastCompleted = -1;

        for (int i = 0; i < steps.size(); i++) {
            try {
                steps.get(i).execute(context);
                lastCompleted = i;
                saga.setLastCompletedStep(i);
                saga.setPayload(serializePayload(context));
                saga.setUpdatedAt(LocalDateTime.now());
                sagaInstanceRepository.save(saga);
            } catch (Exception e) {
                log.error("Saga {} ({}) failed at step {}. Starting compensation from step {}.",
                        saga.getSagaId(), sagaType(), i, lastCompleted, e);
                compensate(saga, context, lastCompleted, steps);
                throw new RuntimeException("Saga " + sagaType() + " failed at step " + i + ": " + e.getMessage(), e);
            }
        }

        saga.setStatus(SagaStatus.COMPLETED);
        saga.setUpdatedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);

        log.info("Saga {} ({}) completed successfully.", saga.getSagaId(), sagaType());
        return result(context);
    }

    private void compensate(SagaInstance saga, C context, int fromStep, List<SagaStep<C>> steps) {
        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setUpdatedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);

        for (int i = fromStep; i >= 0; i--) {
            try {
                steps.get(i).compensate(context);
                log.info("Saga {} compensated step {}.", saga.getSagaId(), i);
            } catch (Exception e) {
                log.error("Saga {} compensation failed at step {} — manual reconciliation required.",
                        saga.getSagaId(), i, e);
                saga.setStatus(SagaStatus.FAILED);
                saga.setUpdatedAt(LocalDateTime.now());
                sagaInstanceRepository.save(saga);
                return;
            }
        }

        saga.setStatus(SagaStatus.COMPENSATED);
        saga.setUpdatedAt(LocalDateTime.now());
        sagaInstanceRepository.save(saga);
    }
}

package com.awbd.cinema.sagas;

public interface SagaStep<C> {
    void execute(C context);
    void compensate(C context);
}

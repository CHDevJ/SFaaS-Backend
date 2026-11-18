package com.sfaasproject.stf.operations.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** No external notifications are sent before a successful commit. */
public class BEventPublisher {
    private final ApplicationEventPublisher publisher;
    private final JdbcBEventOutbox outbox;

    public BEventPublisher(ApplicationEventPublisher publisher) {
        this(publisher, null);
    }

    public BEventPublisher(ApplicationEventPublisher publisher, JdbcBEventOutbox outbox) {
        this.publisher = publisher;
        this.outbox = outbox;
    }

    public void publish(Object event) {
        Long pendingId = outbox == null ? null : outbox.append(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                if (pendingId == null) publisher.publishEvent(event);
                                // outbox 발행은 새 트랜잭션(두 번째 연결)을 연다. UnitOfWork 안이면 연결 반납 뒤로 미룬다.
                                else if (!AfterTransactionDispatch.defer(() -> outbox.dispatch(pendingId)))
                                    outbox.dispatch(pendingId);
                            } catch (RuntimeException failure) {
                                org.slf4j.LoggerFactory.getLogger(BEventPublisher.class)
                                        .error(
                                                "Post-commit event delivery failed: {}",
                                                event.getClass().getSimpleName());
                            }
                        }
                    });
        } else if (pendingId == null) publisher.publishEvent(event);
        else outbox.dispatch(pendingId);
    }
}

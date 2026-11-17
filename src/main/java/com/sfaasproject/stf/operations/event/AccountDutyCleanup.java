package com.sfaasproject.stf.operations.event;

import com.sfaasproject.stf.operations.port.BPorts.Duties;
import com.sfaasproject.stf.system.entity.AuditTargetType;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.user.event.AccountDeletedEvent;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Synchronous listener: account deletion, membership cleanup and audit either all commit or all roll back. */
@Component
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
public class AccountDutyCleanup {
    private final Duties duties;
    private final AuditRecorder audit;
    private final Clock clock;

    public AccountDutyCleanup(Duties duties, AuditRecorder audit, Clock clock) {
        this.duties = duties;
        this.audit = audit;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(AccountDeletedEvent event) {
        for (var duty : duties.excludeAccount(event.accountId(), LocalDate.now(clock))) {
            audit.record(event.deletedBy(), AuditTargetType.DUTY, "당번 " + duty.id(),
                    "담당자 제외(계정 삭제)", Long.toString(event.accountId()), null);
        }
    }
}

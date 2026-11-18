package com.sfaasproject.stf.operations.port;

import com.sfaasproject.stf.alarm.service.AlarmGrade;
import com.sfaasproject.stf.alarm.workflow.AlarmWorkflowState;
import com.sfaasproject.stf.operations.dto.BContract.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Atomic mutation and foreign-domain query boundaries. JDBC adapters implement these ports. */
public final class BPorts {
    private BPorts() {}

    public interface UnitOfWork {
        <T> T execute(Supplier<T> work);
    }

    public interface Users {
        UserBrief active(long id);

        default UserBrief historical(long id) {
            return active(id);
        }

        default boolean connectable(long id) {
            return active(id) != null;
        }

        default boolean passwordChangeRequired(long id) {
            return false;
        }

        Set<Long> maintenanceIds();

        List<UserBrief> notificationUsers();
    }

    public interface Settings {
        int integer(String key, int defaultValue);

        BigDecimal decimal(String key, BigDecimal fallback);
    }

    public record Alarm(
            long id,
            long equipmentId,
            long lineId,
            long dataItemId,
            AlarmGrade grade,
            OffsetDateTime occurredAt,
            AlarmWorkflowState state,
            long version) {}

    public record Mutation(
            String type, Long actorId, String cause, String content, OffsetDateTime at) {}

    public interface Alarms {
        Alarm lock(long id);

        Long save(Alarm before, AlarmWorkflowState after, Mutation mutation);

        List<Long> unacknowledgedBefore(OffsetDateTime before);

        boolean markReminder(long id, OffsetDateTime at);

        List<Long> verifyingInLine(long lineId);

        int unacknowledgedCount();

        int activeCount();
    }

    public record Part(long id, long equipmentId, String name) {}

    public interface Parts {
        Part lock(long id);

        PartReplacementRecord replace(
                Part part, PartReplacementRequest request, UserBrief worker, Long actionId);
    }

    public record Duty(long id, LocalDate from, LocalDate to, long userId) {}

    public interface Duties {
        /**
         * Lock the candidate account before the shared duty lock to match account deletion order.
         */
        void lockAssignee(long userId);

        /** Remove current/future memberships only when every affected period has a replacement. */
        List<Duty> excludeAccount(long userId, LocalDate today);

        List<Duty> lockedSchedules();

        List<Duty> schedules();

        Duty insert(LocalDate from, LocalDate to, long userId);

        void update(Duty duty);

        void delete(long id);

        boolean claimTransition(LocalDate day, long scheduleId);
    }

    public record Connection(String chatId, OffsetDateTime linkedAt) {}

    public record Delivery(
            Long alarmId,
            long userId,
            String type,
            String message,
            boolean success,
            String reason,
            int retryCount,
            OffsetDateTime at) {}

    public interface Telegram {
        Connection connection(long userId);

        void link(long userId, String chatId, OffsetDateTime at);

        void unlink(long userId);

        void record(Delivery delivery);

        TelegramDeliveryPageResponse deliveries(
                LocalDate date, DeliveryResult result, int page, int size);

        TelegramDeliveryPageResponse deliveriesForAlarm(
                LocalDate date, DeliveryResult result, int page, int size, long alarmId);
    }

    public interface LinkTokens {
        void save(
                long userId,
                com.sfaasproject.stf.telegram.service.TelegramLinkTokenService.Issued token,
                Instant now);

        Long consume(String token);
    }

    public interface EventClaims {
        boolean claim(String eventId);
    }

    public interface InventoryRuntime {
        void applyProduction(String eventId, long equipmentId, int quantity, OffsetDateTime at);

        List<InventoryLine> recalculate(OffsetDateTime at);
    }
}

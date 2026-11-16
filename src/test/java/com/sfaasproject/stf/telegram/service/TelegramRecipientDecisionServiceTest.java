package com.sfaasproject.stf.telegram.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sfaasproject.stf.telegram.service.TelegramRecipientDecisionService.Account;
import com.sfaasproject.stf.telegram.service.TelegramRecipientDecisionService.Trigger;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramRecipientDecisionServiceTest {
    private final TelegramRecipientDecisionService service = new TelegramRecipientDecisionService();
    private final List<Account> users = List.of(
            new Account(1, "LINE_MANAGER", true),
            new Account(2, "SYSTEM_ADMIN", true),
            new Account(3, "MAINTENANCE", true),
            new Account(4, "LINE_MANAGER", false));

    @Test
    void eventTypesSelectOnlyConnectedRelevantAccounts() {
        assertEquals(List.of(1L), service.recipients(Trigger.OCCUR, null, users));
        assertEquals(List.of(1L), service.recipients(Trigger.GRADE_UP, null, users));
        assertEquals(List.of(1L, 2L), service.recipients(Trigger.RENOTIFY, null, users));
        assertEquals(List.of(3L), service.recipients(Trigger.RECUR, 3L, users));
        assertEquals(List.of(3L), service.recipients(Trigger.ASSIGN, 3L, users));
        assertEquals(List.of(), service.recipients(Trigger.RECOVER, 3L, users));
    }
}

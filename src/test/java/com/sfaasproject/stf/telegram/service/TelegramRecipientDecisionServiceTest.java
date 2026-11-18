package com.sfaasproject.stf.telegram.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sfaasproject.stf.telegram.service.TelegramRecipientDecisionService.Account;
import com.sfaasproject.stf.telegram.service.TelegramRecipientDecisionService.Trigger;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelegramRecipientDecisionServiceTest {
    private final TelegramRecipientDecisionService service = new TelegramRecipientDecisionService();
    private final List<Account> users = List.of(
            new Account(1, "USER", true),
            new Account(2, "ADMIN", true),
            new Account(3, "USER", true),
            new Account(4, "USER", false));

    @Test
    void eventTypesSelectOnlyConnectedRelevantAccounts() {
        assertEquals(List.of(1L, 2L, 3L), service.recipients(Trigger.OCCUR, null, users));
        assertEquals(List.of(1L, 2L, 3L), service.recipients(Trigger.GRADE_UP, null, users));
        assertEquals(List.of(), service.recipients(Trigger.RENOTIFY, null, users));
        assertEquals(List.of(), service.recipients(Trigger.RECUR, 3L, users));
        assertEquals(List.of(), service.recipients(Trigger.ASSIGN, 3L, users));
        assertEquals(List.of(), service.recipients(Trigger.RECOVER, 3L, users));
    }
}

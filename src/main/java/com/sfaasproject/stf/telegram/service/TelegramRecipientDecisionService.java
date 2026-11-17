package com.sfaasproject.stf.telegram.service;

import java.util.List;
import org.springframework.stereotype.Service;

/** Recipients for the Telegram events fixed in the v5 backend contract. */
@Service
public class TelegramRecipientDecisionService {
    public enum Trigger { OCCUR, GRADE_UP, RENOTIFY, RECUR, ASSIGN, RECOVER }
    public record Account(long id, String role, boolean connected) { }

    public List<Long> recipients(Trigger trigger, Long assigneeId, List<Account> accounts) {
        if (trigger == null || accounts == null) throw new IllegalArgumentException("Trigger and accounts are required");
        if(trigger!=Trigger.OCCUR && trigger!=Trigger.GRADE_UP)return List.of();
        return accounts.stream().filter(Account::connected).map(Account::id).distinct().toList();
    }
}

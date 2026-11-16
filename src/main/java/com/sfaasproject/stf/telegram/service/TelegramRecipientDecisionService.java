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
        return accounts.stream().filter(Account::connected).filter(account -> switch (trigger) {
            case OCCUR, GRADE_UP -> "LINE_MANAGER".equals(account.role());
            case RENOTIFY -> "LINE_MANAGER".equals(account.role())
                    || "SYSTEM_ADMIN".equals(account.role());
            case RECUR, ASSIGN -> assigneeId != null && account.id() == assigneeId;
            case RECOVER -> false;
        }).map(Account::id).distinct().toList();
    }
}

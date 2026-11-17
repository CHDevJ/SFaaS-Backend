package com.sfaasproject.stf.user.event;

/**
 * 가입 신청을 승인하거나 거절했다는 사실. 신청자가 텔레그램을 연결했다면 결과를 알려야 한다(FR0107).
 *
 * <p>트랜잭션 안에서 발행되므로 받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}으로
 * 받아야 하고, 발송이 실패해도 승인·거절은 취소되지 않는다. 거절하면 계정이 삭제되므로 발송에 필요한
 * 값은 모두 이 이벤트에 담았다.
 *
 * @param telegramChatId 신청자가 텔레그램을 연결하지 않았으면 null
 * @param reason 거절 사유. 승인이면 null
 */
public record SignupDecidedEvent(Outcome outcome, long accountId, String loginId, String name,
        String position, String telegramChatId, String reason) {

    public enum Outcome {
        APPROVED,
        REJECTED
    }

    @Override
    public String toString() {
        return "SignupDecidedEvent[outcome=" + outcome + ", accountId=" + accountId + "]";
    }
}

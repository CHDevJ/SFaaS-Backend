package com.sfaasproject.stf.user.event;

/**
 * 계정을 삭제(논리 삭제)했다는 사실. 삭제 트랜잭션 안에서 발행된다.
 *
 * <p>ERD 설계 메모에 따라 삭제한 사람은 현재 당번과 예정 당번에서 빠져야 한다. 당번 테이블은 당번 기능이
 * 소유하므로 그쪽에서 이 이벤트를 받아 처리한다. 같은 트랜잭션에서 처리하려면 {@code @EventListener}로 받는다.
 */
public record AccountDeletedEvent(long accountId, String loginId, String position, long deletedBy) {
}

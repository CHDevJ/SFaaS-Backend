package com.sfaasproject.stf.operations.event;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * 커밋 후 이벤트 발행을 바깥 트랜잭션이 DB 연결을 반납한 뒤로 미룬다.
 *
 * <p>Spring 은 afterCommit·afterCompletion 이 모두 끝난 뒤에 트랜잭션 연결을 반납한다. 그 안에서 outbox 발행이
 * REQUIRES_NEW 로 새 트랜잭션을 열면 요청 하나가 연결 2개를 동시에 쥔다. 동시 요청이 풀 크기를 넘으면 모든 연결이
 * 두 번째 연결을 기다리며 멈춘다(2026-11-18 통합 점검: 수집 27대 동시 전송에서 Hikari active=10, waiting=202).
 *
 * <p>{@link #around} 로 감싼 작업(UnitOfWork) 안에서는 발행을 모아 두었다가 작업이 끝난 뒤 같은 스레드에서
 * 순서대로 실행한다. 감싸지 않은 곳에서는 예전처럼 바로 실행한다.
 */
public final class AfterTransactionDispatch {

    private static final ThreadLocal<Deque<Runnable>> PENDING = new ThreadLocal<>();

    private AfterTransactionDispatch() {
    }

    /** 바깥 작업이 있으면 미루고 true, 없으면 false(호출한 쪽이 바로 실행한다). */
    static boolean defer(Runnable dispatch) {
        Deque<Runnable> pending = PENDING.get();
        if (pending == null) {
            return false;
        }
        pending.addLast(dispatch);
        return true;
    }

    /**
     * work 를 실행하고, 그동안 미룬 발행을 work 가 끝난 뒤(연결 반납 뒤) 실행한다.
     * 이미 바깥 작업 안이면 그 작업에 합류하고 발행은 가장 바깥에서 한 번에 한다.
     */
    public static <T> T around(Supplier<T> work) {
        if (PENDING.get() != null) {
            return work.get();
        }
        Deque<Runnable> pending = new ArrayDeque<>();
        PENDING.set(pending);
        try {
            return work.get();
        } finally {
            PENDING.remove();
            // afterCommit 에서만 쌓이므로 커밋된 작업의 발행만 남는다. 발행 실패는 outbox 재시도가 맡는다.
            while (!pending.isEmpty()) {
                try {
                    pending.pollFirst().run();
                } catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(AfterTransactionDispatch.class)
                            .error("Deferred post-commit event delivery failed");
                }
            }
        }
    }
}

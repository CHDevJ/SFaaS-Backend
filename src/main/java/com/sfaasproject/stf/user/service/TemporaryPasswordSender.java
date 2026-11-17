package com.sfaasproject.stf.user.service;

/**
 * 임시 비밀번호를 사용자 본인의 텔레그램 대화로 보낸다. 메시지에는 비밀번호가 들어 있으므로
 * 구현은 메시지를 로그에 남기면 안 된다.
 */
public interface TemporaryPasswordSender {

    /** 보냈으면 true. 봇이 설정되지 않았거나 재시도까지 실패하면 false. */
    boolean send(String chatId, String message);
}

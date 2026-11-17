package com.sfaasproject.stf.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 텔레그램 봇 설정.
 *
 * <ul>
 *   <li>botUsername: 회원가입 응답의 연결 주소(https://t.me/봇이름?start=토큰)를 만든다. 비어 있으면 회원가입이 실패한다.
 *   <li>botToken: 임시 비밀번호 같은 메시지를 보낼 때 쓴다. 비어 있으면 발송이 실패로 처리된다.
 * </ul>
 * 봇 토큰은 비밀 값이므로 환경변수로만 받고 로그에 남기지 않는다.
 */
@ConfigurationProperties("app.telegram")
public record TelegramProperties(String botUsername, String botToken) {

    public boolean configured() {
        return botUsername != null && !botUsername.isBlank();
    }

    public boolean canSend() {
        return botToken != null && !botToken.isBlank();
    }

    /** 앞의 @ 는 빼고 돌려준다. */
    public String normalizedBotUsername() {
        String name = botUsername.strip();
        return name.startsWith("@") ? name.substring(1) : name;
    }

    @Override
    public String toString() {
        return "TelegramProperties[botUsername=" + botUsername + ", botToken=****]";
    }
}

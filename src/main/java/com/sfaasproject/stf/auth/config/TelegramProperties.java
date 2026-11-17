package com.sfaasproject.stf.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 텔레그램 봇 설정. 회원가입 응답의 연결 주소(https://t.me/봇이름?start=토큰)를 만드는 데 쓴다.
 * 봇 이름이 정해지지 않았으면 비워 두며, 비어 있으면 회원가입은 계정을 만들기 전에 실패한다.
 */
@ConfigurationProperties("app.telegram")
public record TelegramProperties(String botUsername) {

    public boolean configured() {
        return botUsername != null && !botUsername.isBlank();
    }

    /** 앞의 @ 는 빼고 돌려준다. */
    public String normalizedBotUsername() {
        String name = botUsername.strip();
        return name.startsWith("@") ? name.substring(1) : name;
    }
}

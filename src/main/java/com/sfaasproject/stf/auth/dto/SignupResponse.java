package com.sfaasproject.stf.auth.dto;

import com.sfaasproject.stf.auth.service.AuthPolicyService.AccountStatus;
import com.sfaasproject.stf.auth.service.SignupService;
import com.sfaasproject.stf.common.time.KstTime;
import java.time.OffsetDateTime;

/** AUTH-005 응답 데이터(openapi Signup). */
public record SignupResponse(
        long requestId,
        String loginId,
        String name,
        AccountStatus accountStatus,
        OffsetDateTime requestedAt,
        TelegramLinkResponse telegramLink) {

    /** 연결 주소에는 일회용 토큰이 들어 있으므로 로그에 남지 않게 가린다. */
    public record TelegramLinkResponse(String linkUrl, String qrPayload, OffsetDateTime expiresAt) {
        @Override
        public String toString() {
            return "TelegramLinkResponse[redacted]";
        }
    }

    public static SignupResponse from(SignupService.Signup signup) {
        SignupService.TelegramLink link = signup.telegramLink();
        return new SignupResponse(signup.requestId(), signup.loginId(), signup.name(), signup.status(),
                KstTime.toOffset(signup.requestedAt()),
                new TelegramLinkResponse(link.linkUrl(), link.qrPayload(), KstTime.toOffset(link.expiresAt())));
    }
}

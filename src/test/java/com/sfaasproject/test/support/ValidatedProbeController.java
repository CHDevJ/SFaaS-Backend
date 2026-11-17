package com.sfaasproject.test.support;

import com.sfaasproject.stf.common.DomainException;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트에서만 쓰는 컨트롤러. 공통 예외 처리가 입력 오류, 도메인 오류, 예상 못한 오류를 응답 형식에
 * 맞게 바꾸는지 확인한다. 스캔 범위 밖의 패키지에 두고 필요한 테스트에서만 가져온다(@Import).
 */
@RestController
@RequestMapping("/api/v1/test-probe")
public class ValidatedProbeController {

    /** 클래스에 @Validated 를 붙인 방식: 제약 위반이 ConstraintViolationException 으로 나온다. */
    @RestController
    @Validated
    @RequestMapping("/api/v1/test-probe/aop")
    public static class WithClassLevelValidation {
        @GetMapping("/param")
        public String param(@RequestParam @Min(1) int n) {
            return "ok";
        }
    }

    /** 스프링의 메서드 검증 방식: 제약 위반이 HandlerMethodValidationException 으로 나온다. */
    @GetMapping("/param")
    public String param(@RequestParam @Min(1) int n) {
        return "ok";
    }

    @GetMapping("/path/{id}")
    public String path(@PathVariable @Min(1) long id) {
        return "ok";
    }

    @GetMapping("/domain")
    public String domain() {
        throw new DomainException(HttpStatus.CONFLICT, "PROBE_CONFLICT", "probe conflict");
    }

    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("internal-detail-that-must-not-leak");
    }
}

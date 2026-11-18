package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sfaasproject.stf.alarm.controller.AlarmController;
import com.sfaasproject.stf.auth.security.AccessTokenAuthentication;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import com.sfaasproject.stf.common.ApiExceptionHandler;
import com.sfaasproject.stf.duty.controller.DutyController;
import com.sfaasproject.stf.inventory.controller.InventoryController;
import com.sfaasproject.stf.operations.controller.*;
import com.sfaasproject.stf.operations.dto.BContract;
import com.sfaasproject.stf.operations.service.*;
import com.sfaasproject.stf.part.controller.PartController;
import com.sfaasproject.stf.statistics.controller.StatisticsController;
import com.sfaasproject.stf.telegram.controller.TelegramController;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.*;

class BackendBControllerTest {
    static final JsonMapper JSON = JsonMapper.builder().build();

    record Case(
            String id,
            String method,
            String path,
            Map<String, Object> params,
            Object body,
            int status,
            String operation,
            String responseType,
            Object response) {}

    static List<Case> cases() {
        try (var stream =
                BackendBControllerTest.class.getResourceAsStream("/backend-b-api-cases.json")) {
            return Arrays.asList(JSON.readValue(stream, Case[].class));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static Stream<Case> routes() {
        return cases().stream();
    }

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var values = new HashMap<String, Object>();
        for (var c : cases())
            if (c.responseType() != null) {
                try {
                    Class<?> type =
                            Class.forName(BContract.class.getName() + "$" + c.responseType());
                    values.put(c.operation(), JSON.convertValue(c.response(), type));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        var reads = mock(BReadService.class, i -> values.get(i.getMethod().getName()));
        var alarms = mock(AlarmApplicationService.class, i -> values.get(i.getMethod().getName()));
        var parts = mock(PartApplicationService.class, i -> values.get(i.getMethod().getName()));
        var duty = mock(DutyApplicationService.class, i -> values.get(i.getMethod().getName()));
        var telegram =
                mock(TelegramApplicationService.class, i -> values.get(i.getMethod().getName()));
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new AlarmController(reads, alarms),
                                new OverviewController(reads),
                                new InventoryController(reads),
                                new HistoryController(reads),
                                new PartController(reads, parts),
                                new StatisticsController(reads),
                                new DutyController(duty),
                                new TelegramController(telegram))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .build();
        login("ADMIN");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void login(String role) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new AccessTokenAuthentication(new JwtAccessTokenService.Claims(1,
                                        com.sfaasproject.stf.auth.service.AuthPolicyService.Permission.valueOf(role),
                                        "controller-test", java.time.Instant.now(), java.time.Instant.now().plusSeconds(1800))));
    }

    MockHttpServletRequestBuilder request(Case c) {
        var req =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                        HttpMethod.valueOf(c.method()), c.path().replaceAll("\\{[^}]+}", "1"));
        c.params().forEach((k, v) -> req.param(k, v.toString()));
        if (c.body() != null)
            req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(c.body()));
        return req;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void allFortyContractRoutesAreMapped(Case c) throws Exception {
        var result = mvc.perform(request(c)).andExpect(status().is(c.status()));
        if (c.status() != 204)
            result.andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").exists());
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyRouteRejectsAnonymousCaller(Case c) throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(request(c))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void administrationRejectsMaintenance() throws Exception {
        login("USER");
        for (var c : cases())
            if (List.of("DUT-002", "DUT-004", "DUT-005", "TLG-005", "TLG-006").contains(c.id()))
                mvc.perform(request(c)).andExpect(status().isForbidden());
    }

    @Test
    void validatesRequiredRequestFields() throws Exception {
        mvc.perform(
                        patch("/api/v1/alarms/1/acknowledgement")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(
                        post("/api/v1/alarms/1/actions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"cause\":\"x\",\"actionContent\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWrongEnumAndMalformedJson() throws Exception {
        mvc.perform(get("/api/v1/alarms").param("grade", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/alarms/1/false-positive")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}

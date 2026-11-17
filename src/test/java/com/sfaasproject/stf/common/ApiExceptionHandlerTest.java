package com.sfaasproject.stf.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.test.support.ValidatedProbeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/** 공통 예외 처리: 모든 오류가 {success:false, error:{code, message}} 형식으로 나간다. */
@Import({ValidatedProbeController.class, ValidatedProbeController.WithClassLevelValidation.class})
class ApiExceptionHandlerTest extends AbstractAuthIntegrationTest {

    private static final String PROBE = "/api/v1/test-probe";

    private String admin;

    @BeforeEach
    void admin() {
        admin = bearerFor(approvedAccount("admin001", "SYSTEM_ADMIN"));
    }

    @Test
    void a_constraint_violation_from_class_level_validation_is_a_400_not_a_500() throws Exception {
        mockMvc.perform(get(PROBE + "/aop/param").param("n", "0").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void a_method_validation_failure_is_a_400() throws Exception {
        mockMvc.perform(get(PROBE + "/param").param("n", "0").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get(PROBE + "/path/0").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void a_missing_required_parameter_and_a_wrong_type_are_400() throws Exception {
        mockMvc.perform(get(PROBE + "/param").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get(PROBE + "/param").param("n", "abc").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void valid_input_passes() throws Exception {
        mockMvc.perform(get(PROBE + "/param").param("n", "1").header("Authorization", admin))
                .andExpect(status().isOk());
    }

    @Test
    void a_domain_exception_keeps_its_status_and_code() throws Exception {
        mockMvc.perform(get(PROBE + "/domain").header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROBE_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("probe conflict"));
    }

    @Test
    void an_unexpected_exception_is_a_500_with_a_trace_id_and_no_internal_detail() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE + "/boom").header("Authorization", admin))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("internal-detail");
    }

    @Test
    void unknown_routes_and_wrong_methods_keep_their_own_status_instead_of_becoming_500() throws Exception {
        mockMvc.perform(get(PROBE + "/does-not-exist").header("Authorization", admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PROBE + "/domain").header("Authorization", admin))
                .andExpect(status().isMethodNotAllowed());
    }
}

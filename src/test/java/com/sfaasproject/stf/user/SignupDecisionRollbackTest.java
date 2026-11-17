package com.sfaasproject.stf.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.system.service.AuditRecorder;
import com.sfaasproject.stf.user.entity.AccountEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 변경 이력 기록에 실패하면 승인과 거절도 함께 취소된다(이력 없는 변경을 남기지 않는다). */
class SignupDecisionRollbackTest extends AbstractAuthIntegrationTest {

    @MockitoBean
    private AuditRecorder auditRecorder;

    private void auditFails() {
        doThrow(new IllegalStateException("audit store is down")).when(auditRecorder)
                .record(anyLong(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void a_failed_audit_write_cancels_the_approval() throws Exception {
        auditFails();
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        AccountEntity request = pendingAccount("newuser1");

        mockMvc.perform(post("/api/v1/system/signup-requests/" + request.getId() + "/approval")
                        .header("Authorization", bearerFor(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":\"MAINTENANCE\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));

        AccountEntity unchanged = accounts.findById(request.getId()).orElseThrow();
        assertThat(unchanged.isPending()).isTrue();
        assertThat(unchanged.getRole()).isNull();
        assertThat(unchanged.getApprovedBy()).isNull();
    }

    @Test
    void a_failed_audit_write_cancels_the_rejection() throws Exception {
        auditFails();
        AccountEntity admin = approvedAccount("admin001", "SYSTEM_ADMIN");
        AccountEntity request = pendingAccount("newuser1");

        mockMvc.perform(post("/api/v1/system/signup-requests/" + request.getId() + "/rejection")
                        .header("Authorization", bearerFor(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(accounts.findById(request.getId())).as("거절이 취소되어 신청이 남아 있어야 한다").isPresent();
    }
}

package com.sfaasproject.stf.user;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AbstractAuthIntegrationTest;
import com.sfaasproject.stf.user.entity.AccountEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** APR-001 승인 대기 목록. */
class SignupApprovalListApiTest extends AbstractAuthIntegrationTest {

    private static final String URL = "/api/v1/system/signup-requests";

    private String adminBearer;

    @BeforeEach
    void admin() {
        adminBearer = bearerFor(approvedAccount("admin001", "SYSTEM_ADMIN"));
    }

    @Test
    void an_empty_list_has_empty_data_and_zero_totals() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0));
    }

    @Test
    void only_pending_and_undeleted_requests_are_listed_oldest_first() throws Exception {
        AccountEntity first = pendingAccount("first001");
        AccountEntity second = pendingAccount("second01");
        AccountEntity third = pendingAccount("third001");
        approvedAccount("approved1", "MAINTENANCE");
        AccountEntity deleted = pendingAccount("deleted01");
        deleted.markDeleted(LocalDateTime.now(clock));
        accounts.save(deleted);

        mockMvc.perform(get(URL).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].requestId").value(first.getId()))
                .andExpect(jsonPath("$.data[1].requestId").value(second.getId()))
                .andExpect(jsonPath("$.data[2].requestId").value(third.getId()))
                .andExpect(jsonPath("$.data[0].loginId").value("first001"))
                .andExpect(jsonPath("$.data[0].name").value("대기자"))
                .andExpect(jsonPath("$.data[0].accountStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data[0].requestedAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data[0].telegramConnected").value(false))
                .andExpect(jsonPath("$.data[0].telegramLink").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(3));
    }

    @Test
    void the_telegram_connection_state_of_each_applicant_is_shown() throws Exception {
        AccountEntity connected = pendingAccount("linked001");
        connected.connectTelegram("123456789", LocalDateTime.now(clock));
        accounts.save(connected);
        pendingAccount("unlinked1");

        mockMvc.perform(get(URL).header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data[0].loginId").value("linked001"))
                .andExpect(jsonPath("$.data[0].telegramConnected").value(true))
                .andExpect(jsonPath("$.data[1].telegramConnected").value(false))
                .andExpect(jsonPath("$.data[0].telegramChatId").doesNotExist());
    }

    @Test
    void paging_uses_the_contract_defaults_and_reports_totals() throws Exception {
        for (int i = 1; i <= 25; i++) {
            pendingAccount(String.format("user%04d", i));
        }

        mockMvc.perform(get(URL).header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(25))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
        mockMvc.perform(get(URL).param("page", "1").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].loginId").value("user0021"))
                .andExpect(jsonPath("$.meta.page").value(1));
        mockMvc.perform(get(URL).param("size", "10").param("page", "2").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
        mockMvc.perform(get(URL).param("size", "100").header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(25));
    }

    @Test
    void out_of_range_or_malformed_paging_returns_400() throws Exception {
        String[][] invalid = {{"size", "101"}, {"size", "0"}, {"size", "-5"}, {"page", "-1"},
                {"page", "abc"}, {"size", "many"}};
        for (String[] param : invalid) {
            mockMvc.perform(get(URL).param(param[0], param[1]).header("Authorization", adminBearer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void only_the_system_admin_can_list_requests() throws Exception {
        String manager = bearerFor(approvedAccount("manager1", "LINE_MANAGER"));
        String maintainer = bearerFor(approvedAccount("maint001", "MAINTENANCE"));

        for (String bearer : new String[] {manager, maintainer}) {
            mockMvc.perform(get(URL).header("Authorization", bearer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("AUTH_PERMISSION_DENIED"));
        }
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }
}

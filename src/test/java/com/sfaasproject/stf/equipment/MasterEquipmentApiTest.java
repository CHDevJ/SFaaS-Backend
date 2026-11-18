package com.sfaasproject.stf.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sfaasproject.stf.auth.AuthTestConfig;
import com.sfaasproject.stf.auth.service.AuthPolicyService.Permission;
import com.sfaasproject.stf.auth.service.JwtAccessTokenService;
import com.sfaasproject.stf.equipment.service.EquipmentPhotoStorage;
import com.sfaasproject.stf.equipment.service.EquipmentPhotoValidationService;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.user.entity.AccountEntity;
import com.sfaasproject.stf.user.repository.AccountRepository;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MST-001~004 설비 기준정보 (openapi v7). B 의 H2 스키마(V1 과 같은 컬럼)를 써서
 * 데이터 항목·장착 부품·알람·설비 상태 테이블까지 함께 확인한다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:master-equipment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:backend-b-schema.sql"
})
@AutoConfigureMockMvc
@Import(AuthTestConfig.class)
class MasterEquipmentApiTest {

    private static final String BASE = "/api/v1/master/equipment";
    private static final Path PHOTO_DIR = Path.of("build/test-equipment-photos");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 5, 6, 7};

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MockMvc mvc;
    @Autowired EquipmentPhotoStorage storage;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtAccessTokenService jwt;
    @Autowired Clock clock;
    @Autowired com.sfaasproject.stf.equipment.service.MasterEquipmentService equipmentService;

    AccountEntity manager;
    String token;
    long outer;
    long forging;
    long turning;
    long inner;
    long innerForging;

    @BeforeEach
    void setUp() throws IOException {
        for (String table : List.of("part_replacement", "alarm", "part", "equipment_state", "data_item", "equipment",
                "process", "stock_history", "line", "change_log", "auth_token", "account")) {
            jdbc.update("DELETE FROM " + table);
        }
        if (Files.isDirectory(PHOTO_DIR)) {
            try (Stream<Path> files = Files.list(PHOTO_DIR)) {
                for (Path file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        manager = approved("equser", Permission.USER);
        token = bearer(manager);
        outer = line("외륜", 1);
        forging = process(outer, 1, "단조");
        turning = process(outer, 2, "선삭");
        inner = line("내륜", 2);
        innerForging = process(inner, 1, "단조");
    }

    // ---------- MST-001 ----------

    @Test
    void listShowsActiveEquipmentInLineAndProcessOrderWithCounts() throws Exception {
        long b = equipment("OR-02-1", "외륜 선삭기", turning);
        long a = equipment("OR-01-1", "외륜 단조기", forging);
        long c = equipment("IR-01-1", "내륜 단조기", innerForging);
        long gone = equipment("OR-01-9", "삭제된 설비", forging);
        markDeleted("equipment", gone);
        dataItem(a);
        long removedItem = dataItem(b);
        markDeleted("data_item", removedItem);
        jdbc.update("INSERT INTO equipment_state(equipment_id, status, status_since, last_received_at)"
                + " VALUES (?, 'RUNNING', ?, ?)", a, LocalDateTime.of(2026, 11, 17, 9, 0),
                LocalDateTime.of(2026, 11, 17, 9, 30, 15));

        mvc.perform(get(BASE).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registeredCount").value(3))
                .andExpect(jsonPath("$.data.monitoredCount").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(a))
                .andExpect(jsonPath("$.data.items[0].equipmentCode").value("OR-01-1"))
                .andExpect(jsonPath("$.data.items[0].equipmentType").value("시험기"))
                .andExpect(jsonPath("$.data.items[0].lineName").value("외륜"))
                .andExpect(jsonPath("$.data.items[0].processName").value("단조"))
                .andExpect(jsonPath("$.data.items[0].processOrder").value(1))
                .andExpect(jsonPath("$.data.items[0].communicationMethod").value("MODBUS_TCP"))
                .andExpect(jsonPath("$.data.items[0].photoUrl").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].lastReceivedAt").value("2026-11-17T09:30:15+09:00"))
                .andExpect(jsonPath("$.data.items[1].equipmentId").value(b))
                .andExpect(jsonPath("$.data.items[1].lastReceivedAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[2].equipmentId").value(c))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    void listFiltersByLineAndEquipmentAndPages() throws Exception {
        long a = equipment("OR-01-1", "외륜 단조기", forging);
        long b = equipment("OR-02-1", "외륜 선삭기", turning);
        equipment("IR-01-1", "내륜 단조기", innerForging);
        long gone = equipment("OR-01-9", "삭제된 설비", forging);
        markDeleted("equipment", gone);

        mvc.perform(get(BASE).param("lineId", String.valueOf(outer)).header("Authorization", token))
                .andExpect(jsonPath("$.data.registeredCount").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get(BASE).param("equipmentId", String.valueOf(b)).header("Authorization", token))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(b))
                .andExpect(jsonPath("$.data.registeredCount").value(3));
        mvc.perform(get(BASE).param("equipmentId", String.valueOf(gone)).header("Authorization", token))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get(BASE).param("size", "1").param("page", "1").header("Authorization", token))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].equipmentId").value(b))
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
        assertThat(a).isPositive();
    }

    @Test
    void listReturnsEmptyForUnknownLineAndValidatesPaging() throws Exception {
        // v7 MST-001 은 200/401 만 정의한다. 없는 라인은 빈 목록이다.
        mvc.perform(get(BASE).param("lineId", "999999").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.registeredCount").value(0));
        for (String[] param : new String[][] {{"size", "101"}, {"page", "-1"}, {"lineId", "abc"}}) {
            mvc.perform(get(BASE).param(param[0], param[1]).header("Authorization", token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // ---------- MST-002 ----------

    @Test
    void createStoresEquipmentWithPhoto() throws Exception {
        mvc.perform(createForm("OR-05-1").file(photo("photo.png", "image/png", PNG))
                        .param("serialNumber", "  SN-1  ")
                        .param("registeredAt", "2026-11-17T08:30:00+09:00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.equipmentCode").value("OR-05-1"))
                .andExpect(jsonPath("$.data.equipmentName").value("외륜 연삭기 1호기"))
                .andExpect(jsonPath("$.data.serialNumber").value("SN-1"))
                .andExpect(jsonPath("$.data.lineId").value(outer))
                .andExpect(jsonPath("$.data.processId").value(turning))
                .andExpect(jsonPath("$.data.processOrder").value(2))
                .andExpect(jsonPath("$.data.communicationMethod").value("OPC_UA"))
                .andExpect(jsonPath("$.data.registeredAt").value("2026-11-17T08:30:00+09:00"))
                .andExpect(jsonPath("$.data.lastReceivedAt").doesNotExist())
                // v7 에 사진 조회 주소가 없어 photoUrl 은 비운다.
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM equipment WHERE equip_code = 'OR-05-1'");
        assertThat(row.get("equip_type")).isEqualTo("연삭기");
        assertThat(row.get("comm_type")).isEqualTo("OPC_UA");
        assertThat(((Number) row.get("process_id")).longValue()).isEqualTo(turning);
        String fileName = (String) row.get("photo_path");
        assertThat(fileName).matches("[0-9a-f]{32}\\.png");
        assertThat(Files.readAllBytes(PHOTO_DIR.resolve(fileName))).isEqualTo(PNG);

        assertThat(changes()).containsExactly("EQUIPMENT|OR-05-1|설비 등록|null|외륜 연삭기 1호기|" + manager.getId());
    }

    @Test
    void createWithoutOptionalFieldsUsesCurrentTimeAndNoPhoto() throws Exception {
        Instant before = clock.instant().minusSeconds(1);
        mvc.perform(createForm("OR-05-2").param("serialNumber", "   ")
                        .file(new MockMultipartFile("photo", "", "application/octet-stream", new byte[0])))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serialNumber").doesNotExist())
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist());
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM equipment WHERE equip_code = 'OR-05-2'");
        assertThat(row.get("serial_no")).isNull();
        assertThat(row.get("photo_path")).isNull();
        LocalDateTime registered = ((java.sql.Timestamp) row.get("registered_at")).toLocalDateTime();
        assertThat(registered).isAfterOrEqualTo(LocalDateTime.ofInstant(before, java.time.ZoneId.of("Asia/Seoul"))
                .withNano(0));
    }

    @Test
    void createRejectsCodeAlreadyUsedEvenByDeletedEquipment() throws Exception {
        equipment("OR-01-1", "단조기", forging);
        markDeleted("equipment", equipment("OR-01-9", "삭제된 설비", forging));
        for (String code : List.of("OR-01-1", "OR-01-9")) {
            mvc.perform(createForm(code))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("EQUIPMENT_CODE_DUPLICATE"));
        }
        assertThat(count("equipment")).isEqualTo(2);
    }

    @Test
    void createValidatesInputWithoutStoringAnything() throws Exception {
        List<MockMultipartHttpServletRequestBuilder> invalid = List.of(
                createForm("A".repeat(101)),
                createForm("   "),
                createForm("OR-09-1", "communicationMethod", "C".repeat(101)),
                createForm("OR-09-1", "communicationMethod", " "),
                createForm("OR-09-1", "communicationMethod", null),
                createForm("OR-09-1", "processOrder", "0"),
                createForm("OR-09-1", "lineId", "abc"),
                createForm("OR-09-1", "lineId", null),
                createForm("OR-09-1", "registeredAt", "어제"),
                createForm("OR-09-1", "equipmentName", "가".repeat(201)),
                createForm("OR-09-1", "equipmentType", " "),
                createForm("OR-09-1", "equipmentType", "T".repeat(101)),
                createForm("OR-09-1", "serialNumber", "S".repeat(201)),
                createForm("OR-09-1").file(photo("photo.png", "image/png", "not an image".getBytes())),
                createForm("OR-09-1").file(photo("photo.jpg", "image/png", JPEG)),
                multipart(BASE).param("equipmentCode", "OR-09-1").header("Authorization", token));
        for (MockMultipartHttpServletRequestBuilder request : invalid) {
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("equipment")).isZero();
        assertThat(photoFiles()).isEmpty();
    }

    @Test
    void codeLongerThanDbColumnIsDbErrorNotDuplicate() {
        // v7 은 코드를 100자까지 받지만 DB equip_code 는 varchar(20) 이다. 실패한 저장 뒤 다시 조회하지 않는다.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> equipmentService.create(
                        new com.sfaasproject.stf.equipment.service.MasterEquipmentService.Command("X".repeat(21),
                                "긴 코드", "시험기", null, outer, 1, "MQTT", null, null, false), manager.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("equipment")).isZero();
    }

    @Test
    void createRequiresExistingLineAndProcessOrder() throws Exception {
        // v7 MST-002 응답은 201/400/401/409 뿐이라 본문의 라인·공정이 없으면 400 이다.
        markDeleted("line", inner);
        for (MockMultipartHttpServletRequestBuilder request : List.of(
                createForm("OR-09-1", "lineId", "999999"),
                createForm("OR-09-1", "lineId", String.valueOf(inner), "processOrder", "1"),
                createForm("OR-09-1", "processOrder", "7"))) {
            mvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(count("equipment")).isZero();
    }

    @Test
    void createAcceptsWebpPhotoAndFreeCommunicationMethod() throws Exception {
        mvc.perform(createForm("OR-05-3", "communicationMethod", "EtherNet/IP").file(photo("photo.webp",
                        "image/webp", new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.communicationMethod").value("EtherNet/IP"));
        assertThat(jdbc.queryForObject("SELECT photo_path FROM equipment WHERE equip_code = 'OR-05-3'",
                String.class)).matches("[0-9a-f]{32}\\.webp");
    }

    @Test
    void photoStoredInRolledBackTransactionIsRemovedAndCommittedOneIsKept() throws Exception {
        MockMultipartFile file = photo("photo.png", "image/png", PNG);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        String rolledBack = tx.execute(status -> {
            String name = storage.save(file, EquipmentPhotoValidationService.Format.PNG);
            assertThat(Files.exists(PHOTO_DIR.resolve(name))).isTrue();
            status.setRollbackOnly();
            return name;
        });
        assertThat(Files.exists(PHOTO_DIR.resolve(rolledBack))).isFalse();

        String kept = tx.execute(status -> storage.save(file, EquipmentPhotoValidationService.Format.PNG));
        assertThat(Files.exists(PHOTO_DIR.resolve(kept))).isTrue();
        tx.executeWithoutResult(status -> {
            storage.deleteAfterCommit(kept);
            assertThat(Files.exists(PHOTO_DIR.resolve(kept))).isTrue();
        });
        assertThat(Files.exists(PHOTO_DIR.resolve(kept))).isFalse();
    }

    // ---------- MST-003 ----------

    @Test
    void updateChangesProfileAndMovesLineButNeverTheCode() throws Exception {
        long id = equipment("OR-01-1", "단조기", forging);

        mvc.perform(updateForm(id).param("equipmentCode", "HACK-1")
                        .param("equipmentName", "내륜 단조기 1호기").param("equipmentType", "단조기")
                        .param("serialNumber", "SN-9").param("lineId", String.valueOf(inner))
                        .param("processOrder", "1").param("communicationMethod", "MQTT")
                        .param("registeredAt", "2026-11-01T09:00:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipmentCode").value("OR-01-1"))
                .andExpect(jsonPath("$.data.equipmentName").value("내륜 단조기 1호기"))
                .andExpect(jsonPath("$.data.lineId").value(inner))
                .andExpect(jsonPath("$.data.lineName").value("내륜"))
                .andExpect(jsonPath("$.data.processId").value(innerForging))
                .andExpect(jsonPath("$.data.communicationMethod").value("MQTT"))
                .andExpect(jsonPath("$.data.registeredAt").value("2026-11-01T09:00:00+09:00"));

        assertThat(jdbc.queryForObject("SELECT equip_code FROM equipment WHERE equipment_id = ?", String.class, id))
                .isEqualTo("OR-01-1");
        String actor = "|" + manager.getId();
        assertThat(changes()).contains(
                "EQUIPMENT|OR-01-1|이름|단조기|내륜 단조기 1호기" + actor,
                "EQUIPMENT|OR-01-1|종류|시험기|단조기" + actor,
                "EQUIPMENT|OR-01-1|시리얼 번호|null|SN-9" + actor,
                "EQUIPMENT|OR-01-1|공정 배치|외륜 / 1. 단조|내륜 / 1. 단조" + actor,
                "EQUIPMENT|OR-01-1|통신 방식|MODBUS_TCP|MQTT" + actor).hasSize(6);

        mvc.perform(updateForm(id).param("equipmentName", "내륜 단조기 1호기").param("equipmentType", "단조기")
                        .param("serialNumber", "SN-9").param("lineId", String.valueOf(inner))
                        .param("processOrder", "1").param("communicationMethod", "MQTT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registeredAt").value("2026-11-01T09:00:00+09:00"));
        assertThat(changes()).hasSize(6);
    }

    @Test
    void updateReplacesAndRemovesPhoto() throws Exception {
        long id = equipment("OR-01-1", "단조기", forging);

        mvc.perform(fullUpdate(id).file(photo("a.png", "image/png", PNG))).andExpect(status().isOk());
        String first = photoOf(id);
        assertThat(Files.exists(PHOTO_DIR.resolve(first))).isTrue();

        mvc.perform(fullUpdate(id).file(photo("b.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isOk());
        String second = photoOf(id);
        assertThat(second).endsWith(".jpg").isNotEqualTo(first);
        assertThat(Files.exists(PHOTO_DIR.resolve(first))).isFalse();

        mvc.perform(fullUpdate(id).file(photo("c.png", "image/png", PNG)).param("removePhoto", "true"))
                .andExpect(status().isBadRequest());
        assertThat(photoOf(id)).isEqualTo(second);

        mvc.perform(fullUpdate(id).param("removePhoto", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").doesNotExist());
        assertThat(photoOf(id)).isNull();
        assertThat(photoFiles()).isEmpty();
        assertThat(changes()).contains("EQUIPMENT|OR-01-1|사진|없음|새 사진|" + manager.getId(),
                "EQUIPMENT|OR-01-1|사진|있음|새 사진|" + manager.getId(),
                "EQUIPMENT|OR-01-1|사진|있음|없음|" + manager.getId());
    }

    @Test
    void updateReturnsNotFoundAndValidates() throws Exception {
        long gone = equipment("OR-01-9", "삭제된 설비", forging);
        markDeleted("equipment", gone);
        for (long id : new long[] {gone, 999_999L}) {
            mvc.perform(fullUpdate(id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("EQUIPMENT_NOT_FOUND"));
        }
        long id = equipment("OR-01-1", "단조기", forging);
        // 본문의 라인·공정이 없으면 v7 MST-003 응답 정의에 맞춰 400 이다.
        mvc.perform(fullUpdate(id, "processOrder", "9")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(fullUpdate(id, "communicationMethod", " ")).andExpect(status().isBadRequest());
        mvc.perform(fullUpdate(id, "lineId", "999999")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // ---------- MST-004 ----------

    @Test
    void deleteMarksEquipmentAndItsItemsAndPartsKeepingHistory() throws Exception {
        long id = equipment("OR-01-1", "단조기", forging);
        long item = dataItem(id);
        long part = part(id);
        long otherItem = dataItem(equipment("OR-02-1", "선삭기", turning));
        long closed = alarm(id, item, "CLOSED");

        mvc.perform(delete(BASE + "/" + id).header("Authorization", token)).andExpect(status().isNoContent());

        assertThat(deletedAt("equipment", id)).isNotNull();
        assertThat(deletedAt("data_item", item)).isNotNull();
        assertThat(deletedAt("part", part)).isNotNull();
        assertThat(deletedAt("data_item", otherItem)).isNull();
        assertThat(count("alarm WHERE alarm_id = " + closed)).isEqualTo(1);
        assertThat(changes()).containsExactly("EQUIPMENT|OR-01-1|설비 삭제|사용|삭제(데이터 항목 1개, 장착 부품 1개 함께 삭제)|"
                + manager.getId());
        mvc.perform(delete(BASE + "/" + id).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get(BASE).header("Authorization", token)).andExpect(jsonPath("$.data.registeredCount").value(1));
    }

    @Test
    void deleteRejectsEquipmentWithActiveAlarm() throws Exception {
        long id = equipment("OR-01-1", "단조기", forging);
        alarm(id, dataItem(id), "ACTIVE");
        mvc.perform(delete(BASE + "/" + id).header("Authorization", token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("EQUIPMENT_HAS_ACTIVE_ALARM"));
        assertThat(deletedAt("equipment", id)).isNull();
        assertThat(changes()).isEmpty();
    }

    @Test
    void deletedEquipmentReleasesTheLineForDeletion() throws Exception {
        long id = equipment("IR-01-1", "내륜 단조기", innerForging);
        mvc.perform(delete("/api/v1/master/lines/" + inner).header("Authorization", token))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(delete(BASE + "/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/master/lines/" + inner).header("Authorization", token))
                .andExpect(status().isNoContent());
    }

    // ---------- helpers ----------

    /** 기본 등록 입력에 overrides(이름, 값 쌍)를 덮어쓴다. 값이 null 이면 그 칸을 뺀다. */
    private MockMultipartHttpServletRequestBuilder createForm(String code, String... overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("equipmentCode", code);
        fields.put("equipmentName", "외륜 연삭기 1호기");
        fields.put("equipmentType", "연삭기");
        fields.put("lineId", String.valueOf(outer));
        fields.put("processOrder", "2");
        fields.put("communicationMethod", "OPC_UA");
        return form(multipart(BASE), fields, overrides);
    }

    private MockMultipartHttpServletRequestBuilder updateForm(long id) {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PUT, BASE + "/" + id);
        request.header("Authorization", token);
        return request;
    }

    /** 기본 수정 입력(원래 값과 같음)에 overrides 를 덮어쓴다. */
    private MockMultipartHttpServletRequestBuilder fullUpdate(long id, String... overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("equipmentName", "단조기");
        fields.put("equipmentType", "시험기");
        fields.put("lineId", String.valueOf(outer));
        fields.put("processOrder", "1");
        fields.put("communicationMethod", "MODBUS_TCP");
        return form(multipart(HttpMethod.PUT, BASE + "/" + id), fields, overrides);
    }

    private MockMultipartHttpServletRequestBuilder form(MockMultipartHttpServletRequestBuilder request,
            Map<String, String> fields, String... overrides) {
        for (int i = 0; i < overrides.length; i += 2) {
            fields.put(overrides[i], overrides[i + 1]);
        }
        fields.forEach((name, value) -> {
            if (value != null) {
                request.param(name, value);
            }
        });
        request.header("Authorization", token);
        return request;
    }

    private static MockMultipartFile photo(String name, String type, byte[] bytes) {
        return new MockMultipartFile("photo", name, type, bytes);
    }

    private AccountEntity approved(String loginId, Permission permission) {
        LocalDateTime now = LocalDateTime.now(clock);
        AccountEntity account = AccountEntity.signup(loginId, passwords.encode("MasterEq123!"), loginId, now);
        account.approve(permission.name(), null, now);
        return accounts.save(account);
    }

    private String bearer(AccountEntity account) {
        return "Bearer " + jwt.issue(account.getId(), Permission.valueOf(account.getRole()), clock.instant()).value();
    }

    private long line(String name, int order) {
        return insert("INSERT INTO line(name, line_type, sort_order) VALUES (?, 'PART', ?)", name, order);
    }

    private long process(long lineId, int seq, String name) {
        return insert("INSERT INTO process(line_id, process_seq, name) VALUES (?, ?, ?)", lineId, seq, name);
    }

    private long equipment(String code, String name, long processId) {
        return insert("INSERT INTO equipment(equip_code, name, equip_type, process_id, comm_type)"
                + " VALUES (?, ?, '시험기', ?, 'MODBUS_TCP')", code, name, processId);
    }

    private long dataItem(long equipmentId) {
        return insert("INSERT INTO data_item(equipment_id, item_code, name) VALUES (?, 'coolant_flow', '냉각수 유량')",
                equipmentId);
    }

    private long part(long equipmentId) {
        return insert("INSERT INTO part(equipment_id, name, manage_basis, replace_limit, last_replaced_at)"
                + " VALUES (?, '연삭 숫돌', 'ELAPSED_DAYS', 100, ?)", equipmentId, LocalDateTime.now(clock));
    }

    private long alarm(long equipmentId, long dataItemId, String state) {
        return insert("INSERT INTO alarm(equipment_id, data_item_id, grade, message, trigger_value, limit_value,"
                        + " process_state, occurred_at) VALUES (?, ?, 'CAUTION', '시험 알람', 1, 2, ?, ?)",
                equipmentId, dataItemId, state, LocalDateTime.now(clock));
    }

    private void markDeleted(String table, long id) {
        String key = table.equals("data_item") ? "data_item_id" : table + "_id";
        jdbc.update("UPDATE " + table + " SET deleted_at = ? WHERE " + key + " = ?", LocalDateTime.now(clock), id);
    }

    private Object deletedAt(String table, long id) {
        String key = table.equals("data_item") ? "data_item_id" : table + "_id";
        return jdbc.queryForObject("SELECT deleted_at FROM " + table + " WHERE " + key + " = ?", Object.class, id);
    }

    private String photoOf(long id) {
        return jdbc.queryForObject("SELECT photo_path FROM equipment WHERE equipment_id = ?", String.class, id);
    }

    private List<Path> photoFiles() throws IOException {
        if (!Files.isDirectory(PHOTO_DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(PHOTO_DIR)) {
            return files.toList();
        }
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        return ((Number) keys.getKeyList().get(0).values().iterator().next()).longValue();
    }

    private int count(String fromClause) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + fromClause, Integer.class);
    }

    /** target_type|target_name|field_name|old_value|new_value|changed_by, 기록한 순서대로. */
    private List<String> changes() {
        return jdbc.query("SELECT * FROM change_log ORDER BY change_id", (rs, row) -> String.join("|",
                rs.getString("target_type"), rs.getString("target_name"), rs.getString("field_name"),
                String.valueOf(rs.getString("old_value")), String.valueOf(rs.getString("new_value")),
                String.valueOf(rs.getLong("changed_by"))));
    }
}

package com.sfaasproject.stf.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sfaasproject.stf.auth.AuthTestConfig;
import com.sfaasproject.stf.common.DomainException;
import com.sfaasproject.stf.equipment.service.MasterLineService;
import com.sfaasproject.stf.equipment.service.MasterLineService.LayoutItem;
import com.sfaasproject.stf.equipment.service.MasterLineView;
import com.sfaasproject.stf.telegram.service.RedisTelegramLinkTokenStore;
import com.sfaasproject.stf.user.service.TemporaryPasswordSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 MySQL 8.4 에서 V1~V6 을 Flyway 로 적용한 뒤 운영과 같은 {@code ddl-auto=validate} 로 컨텍스트를 띄운다.
 * 라인·공정·설비 엔티티가 실제 스키마와 맞는지와, 잠금·조회 SQL 이 MySQL 에서 동작하는지를 확인한다.
 *
 * <p>MySqlBackendBTest 와 같은 격리 컨테이너 규칙을 쓴다. STF_TEST_MYSQL_URL 이 없으면 건너뛰고,
 * URL 은 격리용 {@code /stf_b_test?} 데이터베이스여야 한다. 실행할 때마다 새 데이터베이스를 만든다.
 */
@EnabledIfEnvironmentVariable(named = "STF_TEST_MYSQL_URL", matches = ".+")
@SpringBootTest
@Import(AuthTestConfig.class)
class MySqlMasterLineTest {

    @MockitoBean RedisTelegramLinkTokenStore telegramLinkTokenStore;
    @MockitoBean TemporaryPasswordSender temporaryPasswordSender;

    @Autowired MasterLineService lines;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String baseUrl = System.getenv("STF_TEST_MYSQL_URL");
        if (!baseUrl.contains("/stf_b_test?")) {
            throw new IllegalArgumentException("Use the isolated stf_b_test container only");
        }
        String password = System.getenv("STF_TEST_MYSQL_PASSWORD");
        String database = "stf_a_master_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(baseUrl, "root", password))
                .execute("CREATE DATABASE " + database);
        registry.add("spring.datasource.url", () -> baseUrl.replace("/stf_b_test?", "/" + database + "?"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Test
    void entitiesMatchFlywaySchemaAndSeededLinesAreListedInOrder() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class)).isEqualTo(6);

        List<MasterLineView> listed = lines.list();
        List<String> expectedNames = jdbc.queryForList(
                "SELECT name FROM line WHERE deleted_at IS NULL ORDER BY sort_order, line_id", String.class);
        assertThat(listed).extracting(MasterLineView::lineName).containsExactlyElementsOf(expectedNames);
        int placed = listed.stream().mapToInt(view -> view.equipment().size()).sum();
        assertThat(placed).isEqualTo(jdbc.queryForObject(
                "SELECT COUNT(*) FROM equipment WHERE deleted_at IS NULL", Integer.class));
        assertThat(placed).isPositive();
        for (MasterLineView view : listed) {
            assertThat(view.equipment()).isSortedAccordingTo((a, b) -> a.processOrder() != b.processOrder()
                    ? Integer.compare(a.processOrder(), b.processOrder())
                    : Long.compare(a.equipmentId(), b.equipmentId()));
        }
    }

    @Test
    void writeFlowWorksOnMySql() {
        long actor = jdbc.queryForObject("SELECT MIN(account_id) FROM account", Long.class);

        MasterLineView created = lines.create("검증 라인 " + UUID.randomUUID().toString().substring(0, 8), 90, null,
                actor);
        assertThat(jdbc.queryForObject("SELECT line_type FROM line WHERE line_id = ?", String.class,
                created.lineId())).isEqualTo("PART");
        assertThatThrownBy(() -> lines.create(created.lineName(), 91, null, actor))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "LINE_NAME_DUPLICATE");

        MasterLineView renamed = lines.update(created.lineId(), created.lineName() + "B", 92, "ASSEMBLY", actor);
        assertThat(renamed.displayOrder()).isEqualTo(92);
        assertThat(renamed.lineType()).isEqualTo("ASSEMBLY");
        // 이름은 ERD line.name(varchar(50))에 맞춰 50자까지다. 50자는 실제 MySQL 에 저장된다.
        assertThatThrownBy(() -> lines.create("가".repeat(51), 93, null, actor))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
        MasterLineView longest = lines.create("가".repeat(49) + UUID.randomUUID().toString().charAt(0), 93, null,
                actor);
        assertThat(longest.lineName()).hasSize(50);
        lines.delete(longest.lineId(), actor);

        lines.delete(created.lineId(), actor);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM line WHERE line_id = ?", Boolean.class,
                created.lineId())).isTrue();

        // 초기 데이터의 첫 라인에서 설비 한 대를 다음 공정으로 옮겼다가 되돌린다.
        Map<String, Object> target = jdbc.queryForMap("""
                SELECT e.equipment_id, p.line_id, p.process_seq FROM equipment e
                JOIN process p ON p.process_id = e.process_id
                WHERE e.deleted_at IS NULL ORDER BY e.equipment_id LIMIT 1""");
        long equipmentId = ((Number) target.get("equipment_id")).longValue();
        long lineId = ((Number) target.get("line_id")).longValue();
        int seq = ((Number) target.get("process_seq")).intValue();
        Integer other = jdbc.queryForObject(
                "SELECT MIN(process_seq) FROM process WHERE line_id = ? AND process_seq <> ?", Integer.class,
                lineId, seq);
        assertThat(other).isNotNull();

        MasterLineView moved = lines.updateLayout(lineId, List.of(new LayoutItem(equipmentId, other)), actor);
        assertThat(moved.equipment()).filteredOn(item -> item.equipmentId() == equipmentId)
                .singleElement().extracting(MasterLineView.EquipmentSummaryView::processOrder).isEqualTo(other);
        lines.updateLayout(lineId, List.of(new LayoutItem(equipmentId, seq)), actor);

        assertThatThrownBy(() -> lines.delete(lineId, actor))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "LINE_HAS_EQUIPMENT");
        assertThatThrownBy(() -> lines.updateLayout(lineId, List.of(new LayoutItem(equipmentId, 999)), actor))
                .isInstanceOf(DomainException.class).hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM change_log WHERE target_type = 'EQUIPMENT'",
                Integer.class)).isEqualTo(2);
    }
}

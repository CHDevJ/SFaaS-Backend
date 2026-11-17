package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sfaasproject.stf.operations.dto.BContract.*;
import com.sfaasproject.stf.operations.event.*;
import com.sfaasproject.stf.operations.port.BPorts.*;
import com.sfaasproject.stf.operations.service.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
class OpenApiStompContractTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-17T09:00:00+09:00");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static Map<String, Object> spec;
    private static Map<String, Map<String, Object>> topics;
    private final SimpMessagingTemplate socket = mock(SimpMessagingTemplate.class);
    private final AlarmApplicationService alarms = mock(AlarmApplicationService.class);
    private final Settings settings = mock(Settings.class);
    private BEventHandlers handlers;

    @BeforeAll
    static void readV7Contract() throws Exception {
        var options = new LoaderOptions();
        options.setMaxAliasesForCollections(1000);
        spec = new Yaml(new SafeConstructor(options)).load(Files.readString(Path.of("docs/openapi.yaml")));
        assertTrue(((Map<String, Object>) spec.get("info")).get("version").toString().startsWith("7."));
        topics = new LinkedHashMap<>();
        var declarations = (List<Map<String, Object>>) ((Map<?, ?>) spec.get("x-websocket")).get("topics");
        for (var topic : declarations) {
            String destination = topic.get("destination").toString();
            assertNull(topics.put(destination, (Map<String, Object>) topic.get("schema")), "Duplicate topic");
        }
    }

    @BeforeEach
    void setUp() {
        when(settings.integer(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
        when(settings.integer("STOCK_LEVEL_MIN", 30)).thenReturn(45);
        when(settings.integer("STOCK_DANGER_MIN", 10)).thenReturn(15);
        when(alarms.activeCount()).thenReturn(17);
        UnitOfWork direct = new UnitOfWork() {
            public <T> T execute(Supplier<T> work) { return work.get(); }
        };
        handlers = new BEventHandlers(
                id -> true, direct, mock(InventoryRuntime.class), alarms,
                mock(TelegramApplicationService.class), mock(Users.class),
                mock(Telegram.class), socket, settings);
    }

    @Test
    void allSixV7TopicsValidateActualPublishedJson() throws Exception {
        Map<String, Consumer<BEventHandlers>> dispatchers = new LinkedHashMap<>();
        dispatchers.put("/topic/equipment-status", h -> h.equipment(new BEvents.EquipmentChanged(
                "equipment", 1, 2, EquipmentStatus.FAULT, NOW, NOW)));
        dispatchers.put("/topic/measurements/{equipmentId}", h -> h.measurement(new BEvents.MeasurementChanged(
                "measurement", 2, 2, 3, DataValueType.MEASUREMENT, new BigDecimal("25.5"), NOW)));
        dispatchers.put("/topic/alarms", h -> h.alarm(alarm("OCCUR", AlarmProcessStatus.UNACKNOWLEDGED)));
        dispatchers.put("/topic/alarm-counts", h -> h.counts(new BEvents.AlarmCounts("count", 4, 2, NOW)));
        dispatchers.put("/topic/inventory", h -> h.inventory(inventory("12")));
        dispatchers.put("/user/queue/notifications", h -> h.notification(notification("OCCUR", 7L)));
        assertEquals(dispatchers.keySet(), topics.keySet());
        for (var entry : dispatchers.entrySet()) {
            reset(socket);
            entry.getValue().accept(handlers);
            payload(entry.getKey());
            verifyNoMoreInteractions(socket);
        }
    }

    @ParameterizedTest
    @CsvSource({"OCCUR,CREATED", "CREATED,CREATED", "GRADE_UP,GRADE_ESCALATED",
            "GRADE_ESCALATED,GRADE_ESCALATED", "RECOVER,VALUE_RECOVERED",
            "VALUE_RECOVERED,VALUE_RECOVERED", "CLOSE,CLOSED", "CLOSED,CLOSED"})
    void alarmEventNamesAndStatesFollowV7(String internalType, String wireType) throws Exception {
        var state = wireType.equals("CLOSED") ? AlarmProcessStatus.CLOSED : AlarmProcessStatus.ACTION;
        handlers.alarm(alarm(internalType, state));
        var message = payload("/topic/alarms");
        assertEquals(wireType, message.get("eventType"));
        assertEquals(wireType.equals("CLOSED") ? "CLOSED" : "ACTIVE", message.get("processStatus"));
    }

    @Test
    void countsUseAllActiveAlarmsInsteadOfLegacyUnacknowledgedCount() throws Exception {
        handlers.counts(new BEvents.AlarmCounts("counts", 9, 2, NOW));
        var message = payload("/topic/alarm-counts");
        assertEquals(17, ((Number) message.get("activeCount")).intValue());
        verify(alarms).activeCount();
    }

    @ParameterizedTest
    @CsvSource({"15,DANGER", "15.1,CAUTION", "45,CAUTION", "45.1,MAINTAIN"})
    void inventoryUsesCurrentWarningAndDangerSettings(String remaining, String expected) throws Exception {
        handlers.inventory(inventory(remaining));
        var message = payload("/topic/inventory");
        assertEquals(expected, message.get("stage"));
        var thresholds = (Map<String, Object>) message.get("stageThresholds");
        assertEquals(45, ((Number) thresholds.get("inventoryWarningMinutes")).intValue());
        assertEquals(15, ((Number) thresholds.get("inventoryDangerMinutes")).intValue());
    }

    @Test
    void nullableInventoryPredictionsRemainNull() throws Exception {
        handlers.inventory(new BEvents.InventoryChanged("unknown", 4, 1, 300, null, null, NOW));
        var message = payload("/topic/inventory");
        assertNull(message.get("stage"));
        assertNull(message.get("remainingMinutes"));
    }

    @ParameterizedTest
    @CsvSource({"OCCUR,CREATED", "GRADE_UP,GRADE_ESCALATED", "CLOSE,ALARM_CLOSED",
            "CREATED,CREATED", "GRADE_ESCALATED,GRADE_ESCALATED", "ALARM_CLOSED,ALARM_CLOSED"})
    void privateAlarmEventsUseOnlyV7Fields(String internalType, String expectedType) throws Exception {
        handlers.notification(notification(internalType, 7L));
        var message = payload("/user/queue/notifications");
        assertEquals(expectedType, message.get("type"));
        assertEquals(7L, ((Number) message.get("alarmId")).longValue());
        assertFalse(message.containsKey("userId"));
        assertFalse(message.containsKey("message"));
        assertFalse(message.containsKey("unreadCount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSIGN", "ACTION", "FALSE_POSITIVE", "RECUR", "RENOTIFY", "READ"})
    void removedLegacyEventsAreNotPublished(String type) {
        handlers.alarm(alarm(type, AlarmProcessStatus.ACTION));
        handlers.notification(notification(type, 7L));
        verifyNoInteractions(socket);
    }

    @Test
    void removedEffectTopicAndNonAlarmNotificationsAreNotPublished() {
        handlers.effect(new BEvents.EffectChanged("effect", 1, 7, EffectStatus.values()[0], 2, 3, NOW));
        handlers.notification(notification("CREATED", null));
        verifyNoInteractions(socket);
    }

    @Test
    void contractValidationRejectsMissingExtraAndIncorrectFields() throws Exception {
        handlers.counts(new BEvents.AlarmCounts("count", 1, 2, NOW));
        var valid = payload("/topic/alarm-counts");
        var missing = new LinkedHashMap<>(valid);
        missing.remove("activeCount");
        assertThrows(AssertionError.class, () -> validate(topics.get("/topic/alarm-counts"), missing));
        var extra = new LinkedHashMap<>(valid);
        extra.put("unacknowledgedCount", 2);
        assertThrows(AssertionError.class, () -> validate(topics.get("/topic/alarm-counts"), extra));
        var wrongType = new LinkedHashMap<>(valid);
        wrongType.put("activeCount", "17");
        assertThrows(AssertionError.class, () -> validate(topics.get("/topic/alarm-counts"), wrongType));
    }

    private Map<String, Object> payload(String destination) throws Exception {
        var sent = ArgumentCaptor.forClass(Object.class);
        if (destination.startsWith("/user/"))
            verify(socket).convertAndSendToUser(eq("42"), eq("/queue/notifications"), sent.capture());
        else
            verify(socket).convertAndSend(eq(destination.replace("{equipmentId}", "2")), sent.capture());
        Map<String, Object> message = JSON.readValue(JSON.writeValueAsString(sent.getValue()), Map.class);
        validate(topics.get(destination), message);
        return message;
    }

    private static void validate(Map<String, Object> schema, Object value) {
        assertNotNull(schema, "Topic/schema is missing");
        if (schema.containsKey("$ref")) {
            String ref = schema.get("$ref").toString();
            Object resolved = spec;
            for (String part : ref.substring(2).split("/")) resolved = ((Map<?, ?>) resolved).get(part);
            schema = (Map<String, Object>) resolved;
        }
        if (value == null) {
            assertEquals(Boolean.TRUE, schema.get("nullable"), "Unexpected null");
            return;
        }
        if (schema.containsKey("enum")) assertTrue(((List<?>) schema.get("enum")).contains(value), "Enum: " + value);
        switch (schema.get("type").toString()) {
            case "object" -> {
                assertInstanceOf(Map.class, value);
                var object = (Map<String, Object>) value;
                var properties = (Map<String, Map<String, Object>>) schema.get("properties");
                assertTrue(object.keySet().containsAll((List<String>) schema.getOrDefault("required", List.of())));
                assertTrue(properties.keySet().containsAll(object.keySet()), "Undocumented wire field");
                for (var entry : object.entrySet()) validate(properties.get(entry.getKey()), entry.getValue());
            }
            case "string" -> {
                assertInstanceOf(String.class, value);
                if ("date-time".equals(schema.get("format"))) assertDoesNotThrow(() -> OffsetDateTime.parse((String) value));
            }
            case "integer", "number" -> {
                assertInstanceOf(Number.class, value);
                var number = new BigDecimal(value.toString());
                if (schema.get("type").equals("integer")) assertTrue(number.stripTrailingZeros().scale() <= 0);
                if (schema.containsKey("minimum")) assertTrue(number.compareTo(new BigDecimal(schema.get("minimum").toString())) >= 0);
                if (schema.containsKey("maximum")) assertTrue(number.compareTo(new BigDecimal(schema.get("maximum").toString())) <= 0);
            }
            default -> fail("Unsupported schema type: " + schema.get("type"));
        }
    }

    private static BEvents.AlarmChanged alarm(String type, AlarmProcessStatus state) {
        return new BEvents.AlarmChanged("alarm", 3, 7, type, AlarmGrade.CRITICAL, state, NOW);
    }

    private static BEvents.InventoryChanged inventory(String remaining) {
        return new BEvents.InventoryChanged("inventory", 5, 1, 300, InventoryStage.MAINTAIN, new BigDecimal(remaining), NOW);
    }

    private static BEvents.Notification notification(String type, Long alarmId) {
        return new BEvents.Notification("notification", 6, 42, type, alarmId, "internal message", NOW);
    }
}

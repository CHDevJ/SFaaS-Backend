package com.sfaasproject.stf.operations;

import static org.junit.jupiter.api.Assertions.*;

import com.sfaasproject.stf.operations.event.BEvents;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

class OpenApiStompContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void sevenStompSchemasMatchTheActualPublishedRecordFields() throws Exception {
        var options = new LoaderOptions();
        options.setMaxAliasesForCollections(1000);
        Map<String, Object> spec =
                new Yaml(new SafeConstructor(options))
                        .load(Files.readString(Path.of("docs/openapi.yaml")));
        var schemas =
                (Map<String, Map<String, Object>>)
                        ((Map<String, Object>) spec.get("components")).get("schemas");
        var topics =
                (List<Map<String, Object>>)
                        ((Map<String, Object>) spec.get("x-websocket")).get("topics");
        var records =
                Map.of(
                        "EquipmentStatusEvent",
                        BEvents.EquipmentChanged.class,
                        "MeasurementEvent",
                        BEvents.MeasurementChanged.class,
                        "AlarmChangedEvent",
                        BEvents.AlarmChanged.class,
                        "AlarmCountEvent",
                        BEvents.AlarmCounts.class,
                        "InventoryChangedEvent",
                        BEvents.InventoryChanged.class,
                        "AlarmEffectEvent",
                        BEvents.EffectChanged.class,
                        "UserNotificationEvent",
                        BEvents.Notification.class);
        assertEquals(7, topics.size());
        for (var topic : topics) {
            String ref = ((Map<String, String>) topic.get("schema")).get("$ref");
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            assertNotNull(records.get(name));
            var properties = (Map<String, Object>) schemas.get(name).get("properties");
            assertEquals(
                    properties.keySet(),
                    Arrays.stream(records.get(name).getRecordComponents())
                            .map(java.lang.reflect.RecordComponent::getName)
                            .collect(Collectors.toSet()),
                    name);
        }
    }
}

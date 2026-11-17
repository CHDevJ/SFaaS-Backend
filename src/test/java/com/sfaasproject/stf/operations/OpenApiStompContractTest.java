package com.sfaasproject.stf.operations;
import static org.junit.jupiter.api.Assertions.*;
import com.sfaasproject.stf.operations.dto.V7Contract;
import java.nio.file.*;import java.util.*;import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;import org.yaml.snakeyaml.*;import org.yaml.snakeyaml.constructor.SafeConstructor;
class OpenApiStompContractTest {
 @Test @SuppressWarnings("unchecked") void sixV7WireDtosMatchPublishedDestinationsAndEnums()throws Exception{
  var options=new LoaderOptions();options.setMaxAliasesForCollections(1000);
  Map<String,Object> spec=new Yaml(new SafeConstructor(options)).load(Files.readString(Path.of("docs/openapi.yaml")));
  var topics=(List<Map<String,Object>>)((Map<String,Object>)spec.get("x-websocket")).get("topics");
  var types=Map.of("/topic/equipment-status",V7Contract.EquipmentEvent.class,"/topic/measurements/{equipmentId}",V7Contract.MeasurementEvent.class,"/topic/alarms",V7Contract.AlarmEvent.class,"/topic/alarm-counts",V7Contract.AlarmCountEvent.class,"/topic/inventory",V7Contract.InventoryEvent.class,"/user/queue/notifications",V7Contract.PersonalEvent.class);
  assertEquals(6,topics.size());for(var topic:topics){var type=types.get(topic.get("destination"));assertNotNull(type);var schema=(Map<String,Object>)topic.get("schema");var props=(Map<String,Object>)schema.get("properties");assertEquals(props.keySet(),Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
   for(var field:type.getRecordComponents())if(field.getType().isEnum()){var property=(Map<String,Object>)props.get(field.getName());if(property.containsKey("enum"))assertEquals(((List<?>)property.get("enum")).stream().filter(Objects::nonNull).collect(Collectors.toSet()),Arrays.stream(field.getType().getEnumConstants()).map(Object::toString).collect(Collectors.toSet()));}
  }
 }
}

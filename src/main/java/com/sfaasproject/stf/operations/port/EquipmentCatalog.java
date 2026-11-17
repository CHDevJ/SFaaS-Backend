package com.sfaasproject.stf.operations.port;

import java.util.List;

/** A-owned catalogue; historical rows are marked deleted rather than hidden. */
public interface EquipmentCatalog {
    List<V7Context.Line> lines();

    List<V7Context.Equipment> equipment();

    List<V7Context.Item> items();

    void lockEquipment(long equipmentId);
}

package com.example.symbolcourt.engine;

import com.example.symbolcourt.domain.Models.*;

import java.util.*;

class State {
    final LinkRequest request;
    final List<ParsedObject> activeObjects = new ArrayList<>();
    final List<ArchiveMember> extractedMembers = new ArrayList<>();
    final Set<ArchiveMember> extracted = new LinkedHashSet<>();
    final Set<ArchiveMember> skippedMembers = new LinkedHashSet<>();
    final List<PlayEvent> events = new ArrayList<>();
    final List<String> notices = new ArrayList<>();
    final Map<String, List<ElfSymbol>> staticConflicts = new LinkedHashMap<>();
    final Map<String, String> selectedComdatGroups = new LinkedHashMap<>();
    final Map<String, Integer> effectiveOrder = new LinkedHashMap<>();

    State(LinkRequest request) {
        this.request = request;
    }

    int loadOrderOf(String objectId) {
        Integer mapped = effectiveOrder.get(objectId);
        if (mapped != null) {
            return mapped;
        }
        return request.loadOrder().stream()
                .filter(entry -> entry.binaryId().equals(objectId))
                .map(LoadOrderEntry::position)
                .findFirst()
                .orElse(999_999);
    }

    void activate(ParsedObject object, int order, String reason, ArchiveMember member) {
        activeObjects.add(object);
        if (order >= 0) {
            effectiveOrder.put(object.id(), order);
        }
        if (member != null) {
            extractedMembers.add(member);
        }
        activateComdatGroups(object, reason);
        events.add(new PlayEvent(events.size(), "SYMBOL_TABLE_UPDATE", "符号表更新",
                object.displayName() + "：" + reason,
                Map.of("objectId", object.id(), "symbols", object.symbols().size(),
                        "references", object.references().size())));
    }

    private void activateComdatGroups(ParsedObject object, String reason) {
        for (SectionGroup group : object.groups()) {
            if (!group.comdat()) {
                continue;
            }
            selectedComdatGroups.putIfAbsent(group.signature(), object.id() + ":" + group.signature());
        }
    }

    List<VersionNode> allVersionNodes() {
        List<VersionNode> nodes = new ArrayList<>();
        for (ParsedObject object : activeObjects) {
            nodes.addAll(object.versionNodes());
        }
        return List.copyOf(nodes);
    }

    List<VersionEdge> allVersionEdges() {
        List<VersionEdge> edges = new ArrayList<>();
        for (ParsedObject object : activeObjects) {
            edges.addAll(object.versionEdges());
        }
        return List.copyOf(edges);
    }
}

package com.testforge.backend.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum DefectStatus {
    OPEN, IN_PROGRESS, RESOLVED, CLOSED;

    private static final Map<DefectStatus, Set<DefectStatus>> ALLOWED = new EnumMap<>(DefectStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(IN_PROGRESS, CLOSED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(OPEN, RESOLVED));
        ALLOWED.put(RESOLVED, EnumSet.of(OPEN, CLOSED));
        ALLOWED.put(CLOSED, EnumSet.of(OPEN));
    }

    public boolean canTransitionTo(DefectStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public List<DefectStatus> allowedTransitions() {
        return List.copyOf(ALLOWED.get(this));
    }
}
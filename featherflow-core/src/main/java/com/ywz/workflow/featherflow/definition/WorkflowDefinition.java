package com.ywz.workflow.featherflow.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkflowDefinition {

    private final String name;
    private final List<ActivityDefinition> activities;

    public WorkflowDefinition(String name, List<ActivityDefinition> activities) {
        this.name = name;
        this.activities = new ArrayList<ActivityDefinition>(activities);
    }

    public String getName() {
        return name;
    }

    public List<ActivityDefinition> getActivities() {
        return Collections.unmodifiableList(activities);
    }
}

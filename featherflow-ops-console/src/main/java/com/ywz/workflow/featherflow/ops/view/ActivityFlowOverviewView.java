package com.ywz.workflow.featherflow.ops.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActivityFlowOverviewView {

    private final List<ActivityFlowNodeView> nodes;
    private final boolean definitionMissing;
    private final String definitionWarning;

    public ActivityFlowOverviewView(
        List<ActivityFlowNodeView> nodes,
        boolean definitionMissing,
        String definitionWarning
    ) {
        this.nodes = Collections.unmodifiableList(new ArrayList<ActivityFlowNodeView>(nodes));
        this.definitionMissing = definitionMissing;
        this.definitionWarning = definitionWarning;
    }

    public List<ActivityFlowNodeView> getNodes() {
        return nodes;
    }

    public boolean isDefinitionMissing() {
        return definitionMissing;
    }

    public String getDefinitionWarning() {
        return definitionWarning;
    }
}

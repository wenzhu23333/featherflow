package com.ywz.workflow.featherflow.service;

import com.ywz.workflow.featherflow.definition.ActivityDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinition;
import com.ywz.workflow.featherflow.definition.WorkflowDefinitionRegistry;
import java.util.ArrayList;
import java.util.List;

public class WorkflowDefinitionQueryService {

    private final WorkflowDefinitionRegistry definitionRegistry;

    public WorkflowDefinitionQueryService(WorkflowDefinitionRegistry definitionRegistry) {
        this.definitionRegistry = definitionRegistry;
    }

    public List<WorkflowDefinitionStepView> listActivitySteps(String workflowName) {
        WorkflowDefinition definition = definitionRegistry.getRequired(workflowName);
        List<WorkflowDefinitionStepView> steps = new ArrayList<WorkflowDefinitionStepView>();
        int sequence = 1;
        for (ActivityDefinition activity : definition.getActivities()) {
            steps.add(new WorkflowDefinitionStepView(
                sequence,
                definition.getName(),
                activity.getName(),
                activity.getDesc(),
                activity.getHandler(),
                activity.getRetryInterval(),
                activity.getMaxRetryTimes()
            ));
            sequence++;
        }
        return steps;
    }
}

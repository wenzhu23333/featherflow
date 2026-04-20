package com.ywz.workflow.featherflow.definition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "workflow")
class WorkflowDocument {

    @JsonProperty("workflow")
    private WorkflowBody workflow;

    @JsonProperty("workflows")
    private List<WorkflowBody> workflows = new ArrayList<WorkflowBody>();

    @JacksonXmlProperty(isAttribute = true, localName = "name")
    private String name;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "activity")
    private List<ActivityDocument> activity = new ArrayList<ActivityDocument>();

    public WorkflowBody getWorkflow() {
        return workflow;
    }

    public void setWorkflow(WorkflowBody workflow) {
        this.workflow = workflow;
    }

    public List<WorkflowBody> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<WorkflowBody> workflows) {
        this.workflows = workflows;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ActivityDocument> getActivity() {
        return activity;
    }

    public void setActivity(List<ActivityDocument> activity) {
        this.activity = activity;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class WorkflowBody {

        private String name;
        private List<ActivityDocument> activities = new ArrayList<ActivityDocument>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ActivityDocument> getActivities() {
            return activities;
        }

        public void setActivities(List<ActivityDocument> activities) {
            this.activities = activities;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ActivityDocument {

        @JacksonXmlProperty(isAttribute = true, localName = "name")
        private String name;

        @JacksonXmlProperty(isAttribute = true, localName = "handler")
        private String handler;

        @JacksonXmlProperty(isAttribute = true, localName = "retryInterval")
        private String retryInterval;

        @JacksonXmlProperty(isAttribute = true, localName = "maxRetryTimes")
        private Integer maxRetryTimes;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHandler() {
            return handler;
        }

        public void setHandler(String handler) {
            this.handler = handler;
        }

        public String getRetryInterval() {
            return retryInterval;
        }

        public void setRetryInterval(String retryInterval) {
            this.retryInterval = retryInterval;
        }

        public Integer getMaxRetryTimes() {
            return maxRetryTimes;
        }

        public void setMaxRetryTimes(Integer maxRetryTimes) {
            this.maxRetryTimes = maxRetryTimes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JacksonXmlRootElement(localName = "workflows")
    static class WorkflowListDocument {

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "workflow")
        private List<WorkflowDocument> workflows = new ArrayList<WorkflowDocument>();

        public List<WorkflowDocument> getWorkflows() {
            return workflows;
        }

        public void setWorkflows(List<WorkflowDocument> workflows) {
            this.workflows = workflows;
        }
    }
}

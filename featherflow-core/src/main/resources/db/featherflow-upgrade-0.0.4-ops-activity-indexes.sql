alter table activity_instance
    add index idx_activity_instance_workflow_created_modified (workflow_id, gmt_created, gmt_modified, activity_id),
    add index idx_activity_instance_workflow_status_created_modified (workflow_id, status, gmt_created, gmt_modified, activity_id);

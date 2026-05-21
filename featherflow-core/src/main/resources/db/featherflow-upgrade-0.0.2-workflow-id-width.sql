-- Upgrade existing featherflow tables to support full UUID workflow ids.
-- Execute this before deploying code that generates 36-character UUID values.

alter table workflow_instance
    modify workflow_id varchar(64) not null comment 'Workflow instance identifier';

alter table activity_instance
    modify workflow_id varchar(64) not null comment 'Workflow instance identifier',
    modify activity_id varchar(128) not null comment 'Activity attempt identifier';

alter table workflow_operation
    modify workflow_id varchar(64) not null comment 'Workflow instance identifier';

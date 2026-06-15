create table workflow_instance (
    workflow_id varchar(64) primary key,
    biz_id varchar(128) not null,
    biz_key varchar(256),
    workflow_name varchar(128) not null,
    start_node varchar(128) not null,
    gmt_created timestamp not null,
    gmt_modified timestamp not null,
    input clob,
    status varchar(32) not null
);

create index idx_workflow_instance_biz_id on workflow_instance (biz_id);
create index idx_workflow_instance_biz_key on workflow_instance (biz_key);
create index idx_workflow_instance_modified on workflow_instance (gmt_modified);
create index idx_workflow_instance_name_modified on workflow_instance (workflow_name, gmt_modified);
create index idx_workflow_instance_status on workflow_instance (status);

create table activity_instance (
    activity_id varchar(128) primary key,
    workflow_id varchar(64) not null,
    activity_name varchar(128) not null,
    executed_node varchar(128) not null,
    gmt_created timestamp not null,
    gmt_modified timestamp not null,
    input clob,
    output clob,
    status varchar(32) not null
);

create index idx_activity_instance_workflow_created on activity_instance (workflow_id, gmt_created, activity_id);
create index idx_activity_instance_workflow_created_modified on activity_instance (workflow_id, gmt_created, gmt_modified, activity_id);
create index idx_activity_instance_workflow_status_created_modified on activity_instance (workflow_id, status, gmt_created, gmt_modified, activity_id);
create index idx_activity_instance_status on activity_instance (status);
create index idx_activity_instance_workflow_name on activity_instance (workflow_id, activity_name);

create table workflow_operation (
    operation_id bigint auto_increment primary key,
    workflow_id varchar(64) not null,
    operation_type varchar(32) not null,
    input varchar(4096),
    status varchar(32) not null,
    gmt_created timestamp not null,
    gmt_modified timestamp not null
);

create index idx_workflow_operation_workflow_id on workflow_operation (workflow_id);
create index idx_workflow_operation_modified on workflow_operation (gmt_modified);
create index idx_workflow_operation_status_modified on workflow_operation (status, gmt_modified);

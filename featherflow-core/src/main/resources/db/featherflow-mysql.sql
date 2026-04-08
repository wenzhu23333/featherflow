create table workflow_instance (
    workflow_id varchar(19) primary key,
    biz_id varchar(128) not null,
    workflow_name varchar(128) not null,
    start_node varchar(128) not null,
    gmt_created datetime not null,
    gmt_modified datetime not null,
    input longtext not null,
    status varchar(32) not null,
    key idx_workflow_instance_biz_id (biz_id),
    key idx_workflow_instance_name_modified (workflow_name, gmt_modified),
    key idx_workflow_instance_status (status)
) engine=InnoDB default charset=utf8mb4;

create table activity_instance (
    activity_id varchar(64) primary key,
    workflow_id varchar(19) not null,
    activity_name varchar(128) not null,
    executed_node varchar(128) not null,
    gmt_created datetime not null,
    gmt_modified datetime not null,
    input longtext not null,
    output longtext null,
    status varchar(32) not null,
    key idx_activity_instance_workflow_id (workflow_id),
    key idx_activity_instance_status (status),
    key idx_activity_instance_workflow_name (workflow_id, activity_name)
) engine=InnoDB default charset=utf8mb4;

create table workflow_operation (
    operation_id bigint primary key auto_increment,
    workflow_id varchar(19) not null,
    operation_type varchar(32) not null,
    input longtext null,
    status varchar(32) not null,
    gmt_created datetime not null,
    gmt_modified datetime not null,
    key idx_workflow_operation_workflow_id (workflow_id),
    key idx_workflow_operation_status_modified (status, gmt_modified)
) engine=InnoDB default charset=utf8mb4;

create table workflow_lock (
    lock_key varchar(255) primary key,
    owner varchar(128) not null,
    gmt_created datetime not null,
    gmt_modified datetime not null
) engine=InnoDB default charset=utf8mb4;

create table workflow_instance (
    workflow_id varchar(19) primary key comment 'Workflow instance identifier',
    biz_id varchar(128) not null comment 'Business identifier',
    workflow_name varchar(128) not null comment 'Workflow definition name',
    start_node varchar(128) not null comment 'Node that started the workflow',
    gmt_created datetime not null comment 'Creation time',
    gmt_modified datetime not null comment 'Last modification time',
    input longtext comment 'Workflow input context snapshot',
    status varchar(32) not null comment 'Workflow execution status',
    key idx_workflow_instance_biz_id (biz_id),
    key idx_workflow_instance_name_modified (workflow_name, gmt_modified),
    key idx_workflow_instance_status (status)
) engine=InnoDB default charset=utf8mb4 comment='Workflow instance table';

create table activity_instance (
    activity_id varchar(64) primary key comment 'Activity attempt identifier',
    workflow_id varchar(19) not null comment 'Workflow instance identifier',
    activity_name varchar(128) not null comment 'Activity definition name',
    executed_node varchar(128) not null comment 'Node that executed the activity',
    gmt_created datetime not null comment 'Creation time',
    gmt_modified datetime not null comment 'Last modification time',
    input longtext comment 'Activity input context snapshot',
    output longtext comment 'Activity output context snapshot or failure payload',
    status varchar(32) not null comment 'Activity execution status',
    key idx_activity_instance_workflow_id (workflow_id),
    key idx_activity_instance_status (status),
    key idx_activity_instance_workflow_name (workflow_id, activity_name)
) engine=InnoDB default charset=utf8mb4 comment='Activity execution history table';

create table workflow_operation (
    operation_id bigint primary key auto_increment comment 'Workflow operation identifier',
    workflow_id varchar(19) not null comment 'Workflow instance identifier',
    operation_type varchar(32) not null comment 'Operation type',
    input longtext comment 'Operation request payload',
    status varchar(32) not null comment 'Operation processing status',
    gmt_created datetime not null comment 'Creation time',
    gmt_modified datetime not null comment 'Last modification time',
    key idx_workflow_operation_workflow_id (workflow_id),
    key idx_workflow_operation_status_modified (status, gmt_modified)
) engine=InnoDB default charset=utf8mb4 comment='Workflow operation command table';

create table workflow_lock (
    lock_key varchar(255) primary key comment 'Distributed lock key',
    owner varchar(128) not null comment 'Lock owner identifier',
    gmt_created datetime not null comment 'Creation time',
    gmt_modified datetime not null comment 'Last modification time'
) engine=InnoDB default charset=utf8mb4 comment='Workflow distributed lock table';

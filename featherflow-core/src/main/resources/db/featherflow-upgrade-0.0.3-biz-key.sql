alter table workflow_instance
    add column biz_key varchar(256) null comment 'Business object key operated by workflow' after biz_id;

create index idx_workflow_instance_biz_key on workflow_instance (biz_key);

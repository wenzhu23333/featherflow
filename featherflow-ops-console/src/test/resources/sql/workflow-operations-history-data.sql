insert into workflow_instance (workflow_id, biz_id, gmt_created, gmt_modified, input, status, ext_col)
values
    ('wf-detail-0001', 'biz-2001', timestamp '2026-04-01 10:00:00', timestamp '2026-04-01 10:08:00', '{"orderId":"10001"}', 'TERMINATED', '{"definitionName":"orderWorkflow"}'),
    ('wf-detail-0002', 'biz-2002', timestamp '2026-04-01 10:02:00', timestamp '2026-04-01 10:09:00', '{"orderId":"20002"}', 'RUNNING', '{"definitionName":"deliveryWorkflow"}');

insert into workflow_operation (workflow_id, operation_type, input, status, gmt_created, gmt_modified)
values
    ('wf-detail-0001', 'TERMINATE', '{bad-json', 'FAILED', timestamp '2026-04-01 10:06:00', timestamp '2026-04-01 10:06:00'),
    ('wf-detail-0001', 'TERMINATE', '{"operator":"alice","reason":"manual-stop","activityId":"act-002"}', 'SUCCESS', timestamp '2026-04-01 10:08:00', timestamp '2026-04-01 10:08:00'),
    ('wf-detail-0002', 'RETRY', '{"operator":"bob","reason":"manual-retry"}', 'PENDING', timestamp '2026-04-01 10:09:00', timestamp '2026-04-01 10:09:00');

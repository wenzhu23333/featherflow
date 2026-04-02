insert into workflow_instance (workflow_id, biz_id, gmt_created, gmt_modified, input, status, ext_col)
values
    ('wf-running-0001', 'biz-1001', timestamp '2026-04-01 09:00:00', timestamp '2026-04-01 09:08:00', '{"orderId":"1001"}', 'RUNNING', '{"definitionName":"orderWorkflow"}'),
    ('wf-terminated-01', 'biz-1002', timestamp '2026-04-01 09:01:00', timestamp '2026-04-01 09:10:00', '{"orderId":"1002"}', 'TERMINATED', '{"definitionName":"refundWorkflow"}');

insert into activity_instance (activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status)
values
    ('act-0001', 'wf-running-0001', 'createOrder', timestamp '2026-04-01 09:00:00', timestamp '2026-04-01 09:07:00', '{}', '{}', 'SUCCESS'),
    ('act-0002', 'wf-running-0001', 'reserveInventory', timestamp '2026-04-01 09:07:00', timestamp '2026-04-01 09:08:00', '{}', null, 'RUNNING'),
    ('act-0003', 'wf-terminated-01', 'chargePayment', timestamp '2026-04-01 09:01:00', timestamp '2026-04-01 09:10:00', '{}', '{"error":"card declined"}', 'FAILED');

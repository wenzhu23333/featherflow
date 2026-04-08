insert into workflow_instance (workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status)
values
    ('wf-running-0001', 'biz-1001', 'orderWorkflow', '10.9.8.7:host-a:1234:seed', timestamp '2026-04-01 09:00:00', timestamp '2026-04-01 09:08:00', '{"orderId":"1001"}', 'RUNNING'),
    ('wf-terminated-01', 'biz-1002', 'refundWorkflow', '10.9.8.7:host-b:1234:seed', timestamp '2026-04-01 09:01:00', timestamp '2026-04-01 09:10:00', '{"orderId":"1002"}', 'TERMINATED');

insert into activity_instance (
    activity_id,
    workflow_id,
    activity_name,
    executed_node,
    gmt_created,
    gmt_modified,
    input,
    output,
    status
) values
    ('act-0001', 'wf-running-0001', 'createOrder', '10.9.8.7:host-a:1234:seed', timestamp '2026-04-01 09:00:00', timestamp '2026-04-01 09:07:00', '{}', '{"created":true}', 'SUCCESSFUL'),
    ('act-0003', 'wf-terminated-01', 'chargePayment', '10.9.8.7:host-b:1234:seed', timestamp '2026-04-01 09:01:00', timestamp '2026-04-01 09:10:00', '{}', '{"error":"card declined"}', 'FAILED');

insert into workflow_instance (workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status)
values
    ('wf-op-running-001', 'biz-op-001', 'opsWorkflow', '10.9.8.7:host-a:1234:seed', timestamp '2026-04-01 11:00:00', timestamp '2026-04-01 11:05:00', '{"orderId":"op-001"}', 'RUNNING'),
    ('wf-op-terminated', 'biz-op-002', 'opsWorkflow', '10.9.8.7:host-b:1234:seed', timestamp '2026-04-01 11:01:00', timestamp '2026-04-01 11:06:00', '{"orderId":"op-002"}', 'TERMINATED'),
    ('wf-op-human-0001', 'biz-op-003', 'opsWorkflow', '10.9.8.7:host-c:1234:seed', timestamp '2026-04-01 11:02:00', timestamp '2026-04-01 11:07:00', '{"orderId":"op-003"}', 'HUMAN_PROCESSING');

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
    ('act-op-run-001', 'wf-op-running-001', 'reserveInventory', '10.9.8.7:host-a:1234:seed', timestamp '2026-04-01 11:00:00', timestamp '2026-04-01 11:05:00', '{}', '{"reserved":true}', 'SUCCESSFUL'),
    ('act-op-term-010', 'wf-op-terminated', 'createOrder', '10.9.8.7:host-b:1234:seed', timestamp '2026-04-01 11:01:00', timestamp '2026-04-01 11:04:00', '{}', '{"created":true}', 'SUCCESSFUL'),
    ('act-op-term-050', 'wf-op-terminated', 'manualPatch', '10.9.8.8:host-patch:1234:seed', timestamp '2026-04-01 11:03:00', timestamp '2026-04-01 11:10:00', '{}', '{"error":"late patch"}', 'FAILED'),
    ('act-op-term-099', 'wf-op-terminated', 'notifyCustomer', '10.9.8.9:host-notify:1234:seed', timestamp '2026-04-01 11:04:00', timestamp '2026-04-01 11:06:00', '{}', '{"error":"manual stop"}', 'FAILED'),
    ('act-op-human-001', 'wf-op-human-0001', 'manualReview', '10.9.8.7:host-c:1234:seed', timestamp '2026-04-01 11:02:00', timestamp '2026-04-01 11:07:00', '{}', '{"error":"need human review"}', 'FAILED');

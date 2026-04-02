insert into workflow_instance (workflow_id, biz_id, gmt_created, gmt_modified, input, status, ext_col)
values
    ('wf-op-running-001', 'biz-op-001', timestamp '2026-04-01 11:00:00', timestamp '2026-04-01 11:05:00', '{"orderId":"op-001"}', 'RUNNING', '{"definitionName":"opsWorkflow"}'),
    ('wf-op-terminated', 'biz-op-002', timestamp '2026-04-01 11:01:00', timestamp '2026-04-01 11:06:00', '{"orderId":"op-002"}', 'TERMINATED', '{"definitionName":"opsWorkflow"}'),
    ('wf-op-human-0001', 'biz-op-003', timestamp '2026-04-01 11:02:00', timestamp '2026-04-01 11:07:00', '{"orderId":"op-003"}', 'HUMAN_PROCESSING', '{"definitionName":"opsWorkflow"}');

insert into activity_instance (activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status)
values
    ('act-op-run-001', 'wf-op-running-001', 'reserveInventory', timestamp '2026-04-01 11:00:00', timestamp '2026-04-01 11:05:00', '{}', null, 'RUNNING'),
    ('act-op-term-010', 'wf-op-terminated', 'createOrder', timestamp '2026-04-01 11:01:00', timestamp '2026-04-01 11:04:00', '{}', '{}', 'SUCCESS'),
    ('act-op-term-050', 'wf-op-terminated', 'manualPatch', timestamp '2026-04-01 11:03:00', timestamp '2026-04-01 11:10:00', '{}', '{"error":"late patch"}', 'FAILED'),
    ('act-op-term-099', 'wf-op-terminated', 'notifyCustomer', timestamp '2026-04-01 11:04:00', timestamp '2026-04-01 11:06:00', '{}', '{"error":"manual stop"}', 'TERMINATED'),
    ('act-op-human-001', 'wf-op-human-0001', 'manualReview', timestamp '2026-04-01 11:02:00', timestamp '2026-04-01 11:07:00', '{}', '{}', 'HUMAN_PROCESSING');

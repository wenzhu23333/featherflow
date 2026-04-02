insert into workflow_instance (workflow_id, biz_id, gmt_created, gmt_modified, input, status, ext_col)
values (
    'wf-detail-0001',
    'biz-2001',
    timestamp '2026-04-01 10:00:00',
    timestamp '2026-04-01 10:08:00',
    '{"orderId":"10001"}',
    'TERMINATED',
    '{"definitionName":"orderWorkflow"}'
);

insert into activity_instance (activity_id, workflow_id, activity_name, gmt_created, gmt_modified, input, output, status)
values
    ('act-900', 'wf-detail-0001', 'createOrder', timestamp '2026-04-01 10:00:00', timestamp '2026-04-01 10:01:00', '{"phase":"create"}', '{"ok":true}', 'SUCCESS'),
    ('act-100', 'wf-detail-0001', 'reserveInventory', timestamp '2026-04-01 10:01:00', timestamp '2026-04-01 10:03:00', '{"phase":"reserve"}', '{"error":"stock low"}', 'FAILED'),
    ('act-500', 'wf-detail-0001', 'notifyCustomer', timestamp '2026-04-01 10:03:00', timestamp '2026-04-01 10:05:00', '{"phase":"notify"}', '{}', 'TERMINATED');

insert into workflow_operation (workflow_id, operation_type, input, status, gmt_created, gmt_modified)
values
    ('wf-detail-0001', 'TERMINATE', '{bad-json', 'FAILED', timestamp '2026-04-01 10:06:00', timestamp '2026-04-01 10:06:00'),
    ('wf-detail-0001', 'TERMINATE', '{"operator":"alice","reason":"manual-stop","activityId":"act-500"}', 'SUCCESS', timestamp '2026-04-01 10:08:00', timestamp '2026-04-01 10:08:00');

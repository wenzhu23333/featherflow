insert into workflow_instance (workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status)
values (
    'wf-detail-0001',
    'biz-2001',
    'orderWorkflow',
    '10.9.8.7:host-d:1234:seed',
    timestamp '2026-04-01 10:00:00',
    timestamp '2026-04-01 10:08:00',
    '{"orderId":"10001"}',
    'TERMINATED'
);

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
    ('act-900', 'wf-detail-0001', 'createOrder', '10.9.8.7:host-d:1234:seed', timestamp '2026-04-01 10:00:00', timestamp '2026-04-01 10:01:00', '{"phase":"create"}', '{"ok":true}', 'SUCCESSFUL'),
    ('act-100', 'wf-detail-0001', 'reserveInventory', '10.9.8.8:host-e:1234:seed', timestamp '2026-04-01 10:01:00', timestamp '2026-04-01 10:03:00', '{"phase":"reserve"}', '{"reserved":true}', 'SUCCESSFUL'),
    ('act-500', 'wf-detail-0001', 'notifyCustomer', '10.9.8.9:host-f:1234:seed', timestamp '2026-04-01 10:03:00', timestamp '2026-04-01 10:05:00', '{"phase":"notify"}', '{"error":"mail gateway timeout"}', 'FAILED');

insert into workflow_operation (workflow_id, operation_type, input, status, gmt_created, gmt_modified)
values
    ('wf-detail-0001', 'TERMINATE', '{bad-json', 'FAILED', timestamp '2026-04-01 10:06:00', timestamp '2026-04-01 10:06:00'),
    ('wf-detail-0001', 'TERMINATE', '{"operator":"alice","reason":"manual-stop","activityId":"act-500"}', 'SUCCESSFUL', timestamp '2026-04-01 10:08:00', timestamp '2026-04-01 10:08:00');

insert into workflow_instance (workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status)
values (
    'wf-graph-0001',
    'biz-graph-2001',
    'graphWorkflow',
    '10.9.8.10:host-g:1234:seed',
    timestamp '2026-04-01 11:00:00',
    timestamp '2026-04-01 11:08:00',
    '{"orderId":"20001"}',
    'HUMAN_PROCESSING'
);

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
    ('act-g-100', 'wf-graph-0001', 'validateOrder', '10.9.8.10:host-g:1234:seed', timestamp '2026-04-01 11:00:00', timestamp '2026-04-01 11:01:00', '{"phase":"validate","attempt":1}', '{"error":"risk timeout"}', 'FAILED'),
    ('act-g-101', 'wf-graph-0001', 'validateOrder', '10.9.8.11:host-h:1234:seed', timestamp '2026-04-01 11:01:00', timestamp '2026-04-01 11:02:00', '{"phase":"validate","attempt":2}', '{"error":"risk timeout"}', 'FAILED'),
    ('act-g-102', 'wf-graph-0001', 'validateOrder', '10.9.8.12:host-i:1234:seed', timestamp '2026-04-01 11:02:00', timestamp '2026-04-01 11:03:00', '{"phase":"validate","attempt":3}', '{"validated":true}', 'SUCCESSFUL'),
    ('act-g-200', 'wf-graph-0001', 'chargePayment', '10.9.8.13:host-j:1234:seed', timestamp '2026-04-01 11:03:00', timestamp '2026-04-01 11:05:00', '{"phase":"charge"}', '{"error":"payment gateway timeout"}', 'FAILED');

insert into workflow_instance (
    workflow_id,
    biz_id,
    workflow_name,
    start_node,
    gmt_created,
    gmt_modified,
    input,
    status
) values (
    'demo-run-0001',
    'biz-order-2001',
    'demoOrderWorkflow',
    '10.9.8.7:ops-node-a:8421:demo',
    timestamp '2026-04-02 09:00:00',
    timestamp '2026-04-02 09:06:00',
    '{"orderId":"O-2001","customerId":"C-2001","amount":128.50}',
    'RUNNING'
);

insert into workflow_instance (
    workflow_id,
    biz_id,
    workflow_name,
    start_node,
    gmt_created,
    gmt_modified,
    input,
    status
) values (
    'demo-success-01',
    'biz-notify-1001',
    'demoOrderWorkflow',
    '10.9.8.8:ops-node-b:8421:demo',
    timestamp '2026-04-02 08:20:00',
    timestamp '2026-04-02 08:26:00',
    '{"customerId":"C-1001","messageType":"ORDER_CREATED"}',
    'SUCCESSFUL'
);

insert into workflow_instance (
    workflow_id,
    biz_id,
    workflow_name,
    start_node,
    gmt_created,
    gmt_modified,
    input,
    status
) values (
    'demo-human-0001',
    'biz-risk-3001',
    'demoRiskWorkflow',
    '10.9.8.9:ops-node-c:8421:demo',
    timestamp '2026-04-02 10:00:00',
    timestamp '2026-04-02 10:12:00',
    '{"applicationId":"A-3001","userId":"U-901","riskLevel":"HIGH"}',
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
) values (
    'demo-run-act-01-01',
    'demo-run-0001',
    '创建订单',
    '10.9.8.7:ops-node-a:8421:demo',
    timestamp '2026-04-02 09:00:00',
    timestamp '2026-04-02 09:02:00',
    '{"orderId":"O-2001","customerId":"C-2001","amount":128.50}',
    '{"orderId":"O-2001","customerId":"C-2001","amount":128.50,"orderCreated":true}',
    'SUCCESSFUL'
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
) values (
    'demo-success-act-01-01',
    'demo-success-01',
    '校验客户信息',
    '10.9.8.8:ops-node-b:8421:demo',
    timestamp '2026-04-02 08:20:00',
    timestamp '2026-04-02 08:22:00',
    '{"customerId":"C-1001","messageType":"ORDER_CREATED"}',
    '{"customerId":"C-1001","messageType":"ORDER_CREATED","customerValidated":true}',
    'SUCCESSFUL'
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
) values (
    'demo-success-act-02-01',
    'demo-success-01',
    '发送客户通知',
    '10.9.8.8:ops-node-b:8421:demo',
    timestamp '2026-04-02 08:22:30',
    timestamp '2026-04-02 08:26:00',
    '{"customerId":"C-1001","messageType":"ORDER_CREATED","customerValidated":true}',
    '{"customerId":"C-1001","messageType":"ORDER_CREATED","customerValidated":true,"notified":true}',
    'SUCCESSFUL'
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
) values (
    'demo-human-act-01-01',
    'demo-human-0001',
    '风控审核',
    '10.9.8.9:ops-node-c:8421:demo',
    timestamp '2026-04-02 10:00:00',
    timestamp '2026-04-02 10:03:00',
    '{"applicationId":"A-3001","userId":"U-901","riskLevel":"HIGH"}',
    '{"applicationId":"A-3001","userId":"U-901","riskLevel":"HIGH","riskChecked":true}',
    'SUCCESSFUL'
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
) values (
    'demo-human-act-02-01',
    'demo-human-0001',
    '发送审核结果',
    '10.9.8.9:ops-node-c:8421:demo',
    timestamp '2026-04-02 10:03:30',
    timestamp '2026-04-02 10:06:00',
    '{"applicationId":"A-3001","userId":"U-901","riskLevel":"HIGH","riskChecked":true}',
    '{"errorType":"RuntimeException","errorMessage":"risk service timeout","stackTrace":"java.lang.RuntimeException: risk service timeout"}',
    'FAILED'
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
) values (
    'demo-human-act-02-02',
    'demo-human-0001',
    '发送审核结果',
    '10.9.8.10:ops-node-d:8421:demo',
    timestamp '2026-04-02 10:10:00',
    timestamp '2026-04-02 10:12:00',
    '{"applicationId":"A-3001","userId":"U-901","riskLevel":"HIGH","riskChecked":true}',
    '{"errorType":"RuntimeException","errorMessage":"risk service timeout","stackTrace":"java.lang.RuntimeException: risk service timeout"}',
    'FAILED'
);

insert into workflow_operation (
    operation_id,
    workflow_id,
    operation_type,
    input,
    status,
    gmt_created,
    gmt_modified
) values (
    1001,
    'demo-run-0001',
    'START',
    '{"operator":"system","reason":"built-in demo data"}',
    'SUCCESSFUL',
    timestamp '2026-04-02 09:00:00',
    timestamp '2026-04-02 09:00:01'
);

insert into workflow_operation (
    operation_id,
    workflow_id,
    operation_type,
    input,
    status,
    gmt_created,
    gmt_modified
) values (
    2001,
    'demo-success-01',
    'START',
    '{"operator":"system","reason":"built-in demo data"}',
    'SUCCESSFUL',
    timestamp '2026-04-02 08:20:00',
    timestamp '2026-04-02 08:20:01'
);

insert into workflow_operation (
    operation_id,
    workflow_id,
    operation_type,
    input,
    status,
    gmt_created,
    gmt_modified
) values (
    3001,
    'demo-human-0001',
    'START',
    '{"operator":"system","reason":"built-in demo data"}',
    'SUCCESSFUL',
    timestamp '2026-04-02 10:00:00',
    timestamp '2026-04-02 10:00:01'
);

insert into workflow_operation (
    operation_id,
    workflow_id,
    operation_type,
    input,
    status,
    gmt_created,
    gmt_modified
) values (
    3002,
    'demo-human-0001',
    'RETRY',
    '{"operator":"ops-demo","reason":"risk service timeout"}',
    'FAILED',
    timestamp '2026-04-02 10:10:00',
    timestamp '2026-04-02 10:12:00'
);

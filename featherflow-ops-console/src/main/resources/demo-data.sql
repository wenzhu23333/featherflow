insert into workflow_instance (
    workflow_id,
    biz_id,
    gmt_created,
    gmt_modified,
    input,
    status,
    ext_col
) values (
    'demo-run-0001',
    'biz-order-2001',
    timestamp '2026-04-02 09:00:00',
    timestamp '2026-04-02 09:06:00',
    '{"orderId":"O-2001","customerId":"C-2001","amount":128.50}',
    'RUNNING',
    '{"definitionName":"订单履约流程"}'
);

insert into workflow_instance (
    workflow_id,
    biz_id,
    gmt_created,
    gmt_modified,
    input,
    status,
    ext_col
) values (
    'demo-success-01',
    'biz-notify-1001',
    timestamp '2026-04-02 08:20:00',
    timestamp '2026-04-02 08:26:00',
    '{"customerId":"C-1001","messageType":"ORDER_CREATED"}',
    'SUCCESSFUL',
    '{"definitionName":"客户通知流程"}'
);

insert into workflow_instance (
    workflow_id,
    biz_id,
    gmt_created,
    gmt_modified,
    input,
    status,
    ext_col
) values (
    'demo-human-0001',
    'biz-risk-3001',
    timestamp '2026-04-02 10:00:00',
    timestamp '2026-04-02 10:12:00',
    '{"applicationId":"A-3001","userId":"U-901","riskLevel":"HIGH"}',
    'HUMAN_PROCESSING',
    '{"definitionName":"人工审核流程"}'
);

insert into activity_instance (
    activity_id,
    workflow_id,
    activity_name,
    gmt_created,
    gmt_modified,
    input,
    output,
    status
) values (
    'demo-run-act-01',
    'demo-run-0001',
    '创建订单',
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
    gmt_created,
    gmt_modified,
    input,
    output,
    status
) values (
    'demo-success-act-01',
    'demo-success-01',
    '校验客户信息',
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
    gmt_created,
    gmt_modified,
    input,
    output,
    status
) values (
    'demo-success-act-02',
    'demo-success-01',
    '发送客户通知',
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
    gmt_created,
    gmt_modified,
    input,
    output,
    status
) values (
    'demo-human-act-01',
    'demo-human-0001',
    '风控审核',
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
    gmt_created,
    gmt_modified,
    input,
    output,
    status
) values (
    'demo-human-act-02',
    'demo-human-0001',
    '发送审核结果',
    timestamp '2026-04-02 10:03:30',
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
    '{"operator":"ops-demo","reason":"risk service timeout","activityId":"demo-human-act-02"}',
    'FAILED',
    timestamp '2026-04-02 10:10:00',
    timestamp '2026-04-02 10:12:00'
);

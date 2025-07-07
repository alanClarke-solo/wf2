-- Insert test workflow statuses (these might be reference data)
-- Note: Adjust these based on your actual status enum values

-- Insert sample workflows for testing
INSERT INTO workflow (
    workflow_id,
    external_workflow_id,
    name,
    description,
    status_id,
    start_time,
    end_time,
    created_at,
    updated_at,
    updated_by
) VALUES
(1, 'WF-TEST-001', 'Sample Test Workflow 1', 'First test workflow for integration tests', 1,
 '2024-01-01 10:00:00+00:00', NULL, '2024-01-01 09:00:00+00:00', '2024-01-01 09:00:00+00:00', 'TEST_USER'),

(2, 'WF-TEST-002', 'Sample Test Workflow 2', 'Second test workflow for integration tests', 2,
 '2024-01-01 11:00:00+00:00', '2024-01-01 12:00:00+00:00', '2024-01-01 10:00:00+00:00', '2024-01-01 12:00:00+00:00', 'TEST_USER'),

(3, 'WF-TEST-003', 'Sample Test Workflow 3', 'Third test workflow for integration tests', 1,
 '2024-01-01 12:00:00+00:00', NULL, '2024-01-01 11:00:00+00:00', '2024-01-01 11:00:00+00:00', 'TEST_USER');

-- Insert sample tasks
INSERT INTO task (
    task_id,
    workflow_id,
    task_def_id,
    name,
    description,
    status_id,
    scheduled_time,
    executed_time,
    created_at,
    updated_at
) VALUES
(1, 1, 101, 'Test Task 1.1', 'First task for workflow 1', 1,
 '2024-01-01 10:15:00+00:00', NULL, '2024-01-01 09:15:00+00:00', '2024-01-01 09:15:00+00:00'),

(2, 1, 102, 'Test Task 1.2', 'Second task for workflow 1', 1,
 '2024-01-01 10:30:00+00:00', NULL, '2024-01-01 09:30:00+00:00', '2024-01-01 09:30:00+00:00'),

(3, 2, 201, 'Test Task 2.1', 'First task for workflow 2', 3,
 '2024-01-01 11:15:00+00:00', '2024-01-01 11:45:00+00:00', '2024-01-01 10:15:00+00:00', '2024-01-01 11:45:00+00:00'),

(4, 3, 301, 'Test Task 3.1', 'First task for workflow 3', 1,
 '2024-01-01 12:15:00+00:00', NULL, '2024-01-01 11:15:00+00:00', '2024-01-01 11:15:00+00:00');

-- Insert sample workflow properties
INSERT INTO workflow_properties (
    property_id,
    workflow_id,
    prop_key,
    prop_value,
    created_at
) VALUES
(1, 1, 'priority', 'HIGH', '2024-01-01 09:00:00+00:00'),
(2, 1, 'department', 'ENGINEERING', '2024-01-01 09:00:00+00:00'),
(3, 1, 'environment', 'TEST', '2024-01-01 09:00:00+00:00'),
(4, 2, 'priority', 'MEDIUM', '2024-01-01 10:00:00+00:00'),
(5, 2, 'department', 'QA', '2024-01-01 10:00:00+00:00'),
(6, 3, 'priority', 'LOW', '2024-01-01 11:00:00+00:00'),
(7, 3, 'department', 'OPERATIONS', '2024-01-01 11:00:00+00:00');

-- Insert sample notifications
INSERT INTO notification (
    notification_id,
    workflow_id,
    status_id,
    sent_yn,
    created_at,
    message
) VALUES
(1, 1, 1, 'Y', '2024-01-01 09:05:00+00:00', 'Workflow started successfully'),
(2, 2, 1, 'Y', '2024-01-01 10:05:00+00:00', 'Workflow started successfully'),
(3, 2, 2, 'Y', '2024-01-01 12:05:00+00:00', 'Workflow completed successfully'),
(4, 3, 1, 'Y', '2024-01-01 11:05:00+00:00', 'Workflow started successfully');

-- Reset AUTO_INCREMENT counters to avoid conflicts with test data
ALTER TABLE workflow ALTER COLUMN workflow_id RESTART WITH 100;
ALTER TABLE task ALTER COLUMN task_id RESTART WITH 100;
ALTER TABLE workflow_properties ALTER COLUMN property_id RESTART WITH 100;
ALTER TABLE notification ALTER COLUMN notification_id RESTART WITH 100;
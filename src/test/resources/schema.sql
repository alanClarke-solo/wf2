-- Drop tables if they exist (for clean test runs)
DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS workflow_properties;
DROP TABLE IF EXISTS workflow;
DROP TABLE IF EXISTS notification;

-- Create workflow table
CREATE TABLE workflow (
    workflow_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    external_workflow_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status_id BIGINT NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255)
);

-- Create task table
CREATE TABLE task (
    task_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    task_def_id BIGINT NOT NULL,
    name VARCHAR(255),
    description TEXT,
    status_id BIGINT NOT NULL,
    scheduled_time TIMESTAMP WITH TIME ZONE,
    executed_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (workflow_id) REFERENCES workflow(workflow_id) ON DELETE CASCADE
);

-- Create workflow_properties table
CREATE TABLE workflow_properties (
    property_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    prop_key VARCHAR(255) NOT NULL,
    prop_value TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (workflow_id) REFERENCES workflow(workflow_id) ON DELETE CASCADE,
    UNIQUE(workflow_id, prop_key)
);

-- Create notification table
CREATE TABLE notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    status_id BIGINT NOT NULL,
    sent_yn CHAR(1) NOT NULL DEFAULT 'N',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    message TEXT,
    FOREIGN KEY (workflow_id) REFERENCES workflow(workflow_id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_workflow_status ON workflow(status_id);
CREATE INDEX idx_workflow_external_id ON workflow(external_workflow_id);
CREATE INDEX idx_workflow_created_at ON workflow(created_at);

CREATE INDEX idx_task_workflow_id ON task(workflow_id);
CREATE INDEX idx_task_status ON task(status_id);
CREATE INDEX idx_task_def_id ON task(task_def_id);

CREATE INDEX idx_workflow_props_workflow_id ON workflow_properties(workflow_id);
CREATE INDEX idx_workflow_props_key ON workflow_properties(prop_key);

CREATE INDEX idx_notification_workflow_status ON notification(workflow_id, status_id);
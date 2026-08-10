-- TONPMS Phase 2 schema
-- Target: MySQL 8.0+
-- Tables and columns directly corresponding to the PDF are documented in
-- database/DATABASE_DESIGN.md. Authentication support tables and some
-- operational columns are explicitly marked there as implementation decisions.

CREATE DATABASE IF NOT EXISTS tonpms
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE tonpms;

CREATE TABLE customer (
    customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_number VARCHAR(30) NOT NULL UNIQUE,
    customer_name VARCHAR(120) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    mobile_number VARCHAR(25) NOT NULL UNIQUE,
    customer_type VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(80) NOT NULL,
    identity_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    registration_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_customer_type CHECK (customer_type IN ('INDIVIDUAL','SME','ENTERPRISE')),
    CONSTRAINT chk_identity_status CHECK (identity_status IN ('PENDING','VERIFIED','REJECTED')),
    CONSTRAINT chk_customer_account_status CHECK (account_status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE TABLE telecom_product (
    product_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(30) NOT NULL UNIQUE,
    product_name VARCHAR(160) NOT NULL,
    product_type VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    monthly_price DECIMAL(12,2) NOT NULL,
    activation_fee DECIMAL(12,2) NOT NULL DEFAULT 0,
    contract_period INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_product_price CHECK (monthly_price >= 0 AND activation_fee >= 0),
    CONSTRAINT chk_product_status CHECK (status IN ('ACTIVE','INACTIVE','DISCONTINUED'))
);

CREATE TABLE telecom_order (
    order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_type VARCHAR(30) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    order_status VARCHAR(25) NOT NULL DEFAULT 'CREATED',
    requested_activation_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT chk_order_amount CHECK (total_amount >= 0),
    CONSTRAINT chk_order_payment_status CHECK (payment_status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    CONSTRAINT chk_order_status CHECK (order_status IN ('CREATED','VALIDATED','PAYMENT_PENDING','INVENTORY_RESERVED','PROVISIONING','ACTIVATED','COMPLETED','FAILED','CANCELLED'))
);

CREATE TABLE order_item (
    order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    tax DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES telecom_order(order_id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES telecom_product(product_id),
    CONSTRAINT chk_order_item_values CHECK (quantity > 0 AND unit_price >= 0 AND discount >= 0 AND tax >= 0 AND total_amount >= 0)
);

CREATE TABLE app_user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(255) NOT NULL,
    customer_id BIGINT UNIQUE,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_status CHECK (account_status IN ('ACTIVE','LOCKED','DISABLED')),
    CONSTRAINT chk_user_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT fk_user_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
);

CREATE TABLE app_role (
    role_id SMALLINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(40) NOT NULL UNIQUE,
    role_name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE provisioning_engineer (
    engineer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    engineer_name VARCHAR(120) NOT NULL,
    specialization VARCHAR(80) NOT NULL,
    region VARCHAR(80) NOT NULL,
    availability VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    active_tasks INT NOT NULL DEFAULT 0,
    experience_years INT NOT NULL DEFAULT 0,
    user_id BIGINT UNIQUE,
    CONSTRAINT chk_engineer_availability CHECK (availability IN ('AVAILABLE','UNAVAILABLE','ON_LEAVE')),
    CONSTRAINT chk_engineer_numbers CHECK (active_tasks >= 0 AND experience_years >= 0)
);

ALTER TABLE provisioning_engineer
    ADD CONSTRAINT fk_engineer_user FOREIGN KEY (user_id) REFERENCES app_user(user_id);

CREATE TABLE app_user_role (
    user_id BIGINT NOT NULL,
    role_id SMALLINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(user_id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role(role_id)
);

CREATE TABLE inventory_item (
    inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_code VARCHAR(40) NOT NULL UNIQUE,
    item_type VARCHAR(30) NOT NULL,
    serial_number VARCHAR(80) UNIQUE,
    warehouse VARCHAR(100) NOT NULL,
    location VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    assigned_order_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_order FOREIGN KEY (assigned_order_id) REFERENCES telecom_order(order_id),
    CONSTRAINT chk_inventory_type CHECK (item_type IN ('SIM','ESIM','ROUTER','MODEM','ONT','MOBILE_DEVICE','NETWORK_DEVICE')),
    CONSTRAINT chk_inventory_status CHECK (status IN ('AVAILABLE','RESERVED','ALLOCATED','INSTALLED','DAMAGED','RETURNED'))
);

CREATE TABLE order_payment (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    transaction_reference VARCHAR(80) NOT NULL UNIQUE,
    amount DECIMAL(12,2) NOT NULL,
    payment_mode VARCHAR(20) NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES telecom_order(order_id),
    CONSTRAINT chk_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_payment_mode CHECK (payment_mode IN ('UPI','CARD','NET_BANKING','BANK_TRANSFER')),
    CONSTRAINT chk_payment_status CHECK (status IN ('INITIATED','SUCCESS','FAILED','REFUNDED'))
);

CREATE TABLE provisioning_request (
    provisioning_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    service_id VARCHAR(60) NOT NULL,
    provisioning_type VARCHAR(30) NOT NULL,
    network_element VARCHAR(120),
    requested_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_date TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(500),
    engineer_id BIGINT NULL,
    CONSTRAINT fk_provisioning_order FOREIGN KEY (order_id) REFERENCES telecom_order(order_id),
    CONSTRAINT fk_provisioning_engineer FOREIGN KEY (engineer_id) REFERENCES provisioning_engineer(engineer_id),
    CONSTRAINT chk_provisioning_type CHECK (provisioning_type IN ('SIM_ACTIVATION','ESIM_ACTIVATION','MOBILE_SERVICE','BROADBAND','VPN','5G_SERVICE')),
    CONSTRAINT chk_provisioning_status CHECK (status IN ('PENDING','IN_PROGRESS','SUCCESS','FAILED'))
);

CREATE TABLE customer_subscription (
    subscription_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    service_id VARCHAR(60) NOT NULL UNIQUE,
    service_type VARCHAR(30) NOT NULL,
    activation_date TIMESTAMP NULL,
    termination_date TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_subscription_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_subscription_order FOREIGN KEY (order_id) REFERENCES telecom_order(order_id),
    CONSTRAINT chk_subscription_status CHECK (status IN ('PENDING','ACTIVE','TERMINATED'))
);

CREATE TABLE notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NULL,
    recipient_user_id BIGINT NULL,
    notification_type VARCHAR(40) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT fk_notification_user FOREIGN KEY (recipient_user_id) REFERENCES app_user(user_id),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING','SENT','FAILED')),
    CONSTRAINT chk_notification_recipient CHECK (customer_id IS NOT NULL OR recipient_user_id IS NOT NULL)
);

CREATE TABLE audit_log (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    details VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(user_id)
);

CREATE TABLE login_history (
    login_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username_attempted VARCHAR(80) NOT NULL,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL,
    ip_address VARCHAR(45),
    failure_reason VARCHAR(255),
    CONSTRAINT fk_login_user FOREIGN KEY (user_id) REFERENCES app_user(user_id)
);

CREATE TABLE otp_challenge (
    otp_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    attempts INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES app_user(user_id),
    CONSTRAINT chk_otp_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_order_customer_status ON telecom_order(customer_id, order_status);
CREATE INDEX idx_order_date_status ON telecom_order(order_date, order_status);
CREATE INDEX idx_inventory_type_status_warehouse ON inventory_item(item_type, status, warehouse);
CREATE INDEX idx_provisioning_status_date ON provisioning_request(status, requested_date);
CREATE INDEX idx_payment_order_status ON order_payment(order_id, status);
CREATE INDEX idx_login_history_username_date ON login_history(username_attempted, attempted_at);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);

INSERT INTO app_role (role_code, role_name) VALUES
    ('CUSTOMER', 'Customer'),
    ('ORDER_ADMINISTRATOR', 'Order Administrator'),
    ('PROVISIONING_ENGINEER', 'Provisioning Engineer'),
    ('INVENTORY_ADMINISTRATOR', 'Inventory Administrator');

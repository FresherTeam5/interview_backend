INSERT INTO system_settings
    (setting_key, setting_value, value_type, description, updated_at)
VALUES
    ('BACKGROUND_JOB_RETENTION_DAYS', '90', 'INTEGER',
     'Number of days to retain completed background job run history', UTC_TIMESTAMP(6));

INSERT INTO governance_tenants
    (id, tenant_id, display_name, slug, state, policy_version, revision, created_at, updated_at)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     'OpenEIP Default',
     'default',
     'ACTIVE',
     'governance-v1',
     0,
     CURRENT_TIMESTAMP(6),
     CURRENT_TIMESTAMP(6));

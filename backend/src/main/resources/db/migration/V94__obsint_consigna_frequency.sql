ALTER TABLE obsint_consigna ADD COLUMN frequency varchar(24) NOT NULL DEFAULT 'EVERY_SHIFT';
ALTER TABLE obsint_consigna ADD CONSTRAINT ck_obsint_consigna_frequency
    CHECK (frequency IN ('EVERY_SHIFT', 'EVERY_2_HOURS', 'EVERY_4_HOURS', 'DAILY'));

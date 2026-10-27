ALTER TABLE obsint_consigna ADD COLUMN description varchar(1000) NOT NULL DEFAULT '';
UPDATE obsint_consigna SET description = instruction WHERE description = '';

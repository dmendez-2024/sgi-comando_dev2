-- UAT authentication repair.
-- Password for all UAT users: CajamarcaUAT!2026
-- This is a valid bcrypt MCF hash for that password. V2 is intentionally left unchanged
-- so existing UAT databases can migrate forward without Flyway checksum conflicts.
UPDATE app_user
SET password_hash = '$2a$10$56cltNbyR/gzUjN.SM0SQ.CgvYX1/yg67XjMMoe6qi9M9cjxZieFO'
WHERE username IN ('don','dnacional','dzonal','coord','asistente','supervisor','agente','cliente');

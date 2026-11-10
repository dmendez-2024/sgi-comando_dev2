ALTER TABLE incident_notification
    ADD COLUMN category_catalog_id uuid REFERENCES incident_category_catalog(id),
    ADD COLUMN subcategory_catalog_id uuid REFERENCES incident_subcategory_catalog(id),
    ADD COLUMN incident_type_catalog_id uuid REFERENCES incident_type_catalog(id),
    ADD COLUMN severity_catalog_id uuid REFERENCES incident_severity_catalog(id);


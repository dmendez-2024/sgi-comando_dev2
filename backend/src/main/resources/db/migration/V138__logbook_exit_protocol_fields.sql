alter table operator_logbook_record
    add column if not exists white_list_entry_id uuid,
    add column if not exists exit_protocol_id uuid,
    add column if not exists exit_protocol_version integer,
    add column if not exists exit_accreditation_id uuid,
    add column if not exists exit_field_values_json text not null default '{}',
    add column if not exists exit_comments varchar(500);

alter table operator_logbook_record
    add constraint fk_operator_logbook_white_list_entry
        foreign key (white_list_entry_id) references access_control_entry(id),
    add constraint fk_operator_logbook_exit_protocol
        foreign key (exit_protocol_id) references logbook_protocol(id),
    add constraint fk_operator_logbook_exit_accreditation
        foreign key (exit_accreditation_id) references logbook_accreditation(id);

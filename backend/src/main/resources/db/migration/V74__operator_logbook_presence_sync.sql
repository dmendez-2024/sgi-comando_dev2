alter table operator_logbook_record
    add column if not exists exited_at timestamptz,
    add column if not exists exited_by_employee_id uuid,
    add column if not exists exited_by_username varchar(160),
    add column if not exists exit_operation_id uuid,
    add column if not exists record_version bigint not null default 1,
    add column if not exists point_version bigint;

update operator_logbook_record
set status='INSIDE'
where status='REGISTERED';

create table if not exists operator_logbook_point_version (
    instance_country_id uuid not null,
    point_id uuid not null,
    current_version bigint not null default 0,
    updated_at timestamptz not null default current_timestamp,
    primary key(instance_country_id,point_id)
);

insert into operator_logbook_point_version(instance_country_id,point_id,current_version,updated_at)
select instance_country_id,point_id,count(*),current_timestamp
from operator_logbook_record
group by instance_country_id,point_id
on conflict(instance_country_id,point_id) do nothing;

with numbered as (
    select id,row_number() over(partition by instance_country_id,point_id order by captured_at,id) as version_no
    from operator_logbook_record
    where point_version is null
)
update operator_logbook_record r
set point_version=n.version_no
from numbered n
where r.id=n.id;

create unique index if not exists ux_operator_logbook_exit_operation
    on operator_logbook_record(instance_country_id,exit_operation_id)
    where exit_operation_id is not null;

create index if not exists ix_operator_logbook_point_status_time
    on operator_logbook_record(instance_country_id,point_id,status,captured_at desc);

create index if not exists ix_operator_logbook_point_version
    on operator_logbook_record(instance_country_id,point_id,point_version);

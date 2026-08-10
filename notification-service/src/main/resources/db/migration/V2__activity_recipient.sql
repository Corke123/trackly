alter table activity
    add column recipient_id      varchar(100),
    add column recipient_message varchar(500);

create index idx_activity_recipient on activity (recipient_id, id) where recipient_id is not null;

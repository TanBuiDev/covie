alter table billing_reminder_dispatches
    drop constraint if exists ck_billing_reminder_days_before;
alter table billing_reminder_dispatches
    add constraint ck_billing_reminder_days_before check (days_before between 1 and 60);

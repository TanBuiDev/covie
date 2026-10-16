-- Remove any legacy CHECK constraints still restricting offsets to 1/3/7 days.
-- Retain the explicit new 1..60 constraint from V031.
do $$
declare old_name text;
begin
    for old_name in
        select c.conname from pg_constraint c
        where c.conrelid='billing_reminder_dispatches'::regclass
          and c.contype='c'
          and pg_get_constraintdef(c.oid) like '%days_before%'
          and c.conname <> 'ck_billing_reminder_days_before'
    loop
        execute format('alter table billing_reminder_dispatches drop constraint %I',old_name);
    end loop;
end;
$$;

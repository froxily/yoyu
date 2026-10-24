ALTER TABLE jobs ADD CONSTRAINT jobs_id_user_id_uq UNIQUE (id, user_id);

ALTER TABLE shifts
  ADD CONSTRAINT shifts_job_owner_fk FOREIGN KEY (job_id, user_id) REFERENCES jobs (id, user_id) ON DELETE RESTRICT;

CREATE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE TRIGGER shifts_set_updated_at
  BEFORE UPDATE ON shifts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

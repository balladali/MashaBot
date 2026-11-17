-- Runtime credentials are provisioned separately; migrations never run as this role.
DO $$ BEGIN
 IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'masha_app') THEN
  CREATE ROLE masha_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
 END IF;
 IF EXISTS (SELECT FROM pg_roles WHERE rolname='masha_app' AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb)) THEN
  RAISE EXCEPTION 'masha_app must be unprivileged';
 END IF;
END $$;
REVOKE ALL ON chat_messages, chat_memory_settings FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO masha_app;
GRANT SELECT, INSERT, UPDATE ON chat_messages, chat_memory_settings TO masha_app;
GRANT USAGE ON SEQUENCE chat_messages_id_seq TO masha_app;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages FORCE ROW LEVEL SECURITY;
ALTER TABLE chat_memory_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_memory_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY chat_messages_scope ON chat_messages TO masha_app
 USING (chat_id = NULLIF(current_setting('app.chat_id', true), '')::bigint)
 WITH CHECK (chat_id = NULLIF(current_setting('app.chat_id', true), '')::bigint);
CREATE POLICY chat_memory_settings_scope ON chat_memory_settings TO masha_app
 USING (chat_id = NULLIF(current_setting('app.chat_id', true), '')::bigint)
 WITH CHECK (chat_id = NULLIF(current_setting('app.chat_id', true), '')::bigint);

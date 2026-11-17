package ru.balladali.mashabot.memory;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.sql.*;
import java.util.*;

@Service
public class ChatMemoryService {
    private final MemoryProperties p;

    public ChatMemoryService(MemoryProperties p) {
        this.p = p;
        p.validate();
        if (p.isEnabled()) {
            try (var c = open();
                    var q = c.createStatement();
                    var rows =
                            q.executeQuery(
                                    "SELECT rolsuper,rolbypassrls,rolcreaterole,rolcreatedb FROM"
                                        + " pg_roles WHERE rolname=current_user")) {
                if (!rows.next()
                        || rows.getBoolean(1)
                        || rows.getBoolean(2)
                        || rows.getBoolean(3)
                        || rows.getBoolean(4))
                    throw new IllegalStateException(
                            "Memory runtime requires an unprivileged database role");
            } catch (SQLException e) {
                throw new IllegalStateException("Memory database validation failed", e);
            }
            try (var c = open();
                    var q = c.createStatement();
                    var rows =
                            q.executeQuery(
                                    "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON"
                                        + " n.oid=c.relnamespace WHERE n.nspname='public' AND"
                                        + " c.relname IN ('chat_messages','chat_memory_settings')"
                                        + " AND c.relrowsecurity AND c.relforcerowsecurity AND"
                                        + " c.relowner <> (SELECT oid FROM pg_roles WHERE"
                                        + " rolname=current_user) AND NOT EXISTS (SELECT FROM"
                                        + " pg_auth_members WHERE member=(SELECT oid FROM pg_roles"
                                        + " WHERE rolname=current_user))")) {
                rows.next();
                if (rows.getInt(1) != 2)
                    throw new IllegalStateException(
                            "Memory tables require forced RLS and a separate owner; runtime role"
                                + " must have no role memberships");
            } catch (SQLException e) {
                throw new IllegalStateException("Memory RLS validation failed", e);
            }
        }
    }

    public boolean enabled() {
        return p.isEnabled();
    }

    private Connection open() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", p.getUsername());
        props.setProperty("password", p.getPassword());
        props.setProperty("tcpNoDelay", "true");
        props.setProperty("connectTimeout", "3");
        props.setProperty("socketTimeout", "5");
        props.setProperty("options", "-c statement_timeout=3000 -c lock_timeout=2000");
        return DriverManager.getConnection(p.getUrl(), props);
    }

    private Connection scoped(long chat) throws SQLException {
        Connection c = open();
        try {
            c.setAutoCommit(false);
            try (var q = c.prepareStatement("SELECT set_config('app.chat_id', ?, true)")) {
                q.setString(1, Long.toString(chat));
                q.execute();
            }
            return c;
        } catch (SQLException e) {
            c.close();
            throw e;
        }
    }

    private void lock(Connection c, long chat) throws SQLException {
        try (var q = c.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            q.setLong(1, chat);
            q.execute();
        }
    }

    private boolean capture(Connection c, long chat) throws SQLException {
        if (!enabled() || !p.getCapture().isEnabled()) return false;
        try (var q =
                c.prepareStatement(
                        "SELECT capture_enabled FROM chat_memory_settings WHERE chat_id=?")) {
            q.setLong(1, chat);
            try (var rs = q.executeQuery()) {
                return rs.next() ? rs.getBoolean(1) : p.getCapture().isDefaultChatEnabled();
            }
        }
    }

    public String status(long chat) throws SQLException {
        if (!enabled()) return "Память отключена глобально.";
        try (var c = scoped(chat)) {
            return "Чтение памяти включено. Сохранение: " + capture(c, chat);
        }
    }

    public void setCapture(long chat, boolean value) throws SQLException {
        if (!enabled()) return;
        try (var c = scoped(chat)) {
            lock(c, chat);
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO chat_memory_settings(chat_id,capture_enabled) VALUES (?,?)"
                                + " ON CONFLICT(chat_id) DO UPDATE SET"
                                + " capture_enabled=excluded.capture_enabled,updated_at=now()")) {
                q.setLong(1, chat);
                q.setBoolean(2, value);
                q.executeUpdate();
            }
            c.commit();
        }
    }

    public void save(Message m, boolean outgoing) {
        if (!enabled() || m == null || !p.getCapture().isEnabled()) return;
        try {
            save(ChatMessage.from(m, outgoing));
            if (m.getReplyToMessage() != null
                    && Objects.equals(m.getChatId(), m.getReplyToMessage().getChatId()))
                save(ChatMessage.from(m.getReplyToMessage(), false));
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass())
                    .warn("event=memory_persistence_error type={}", e.getClass().getSimpleName());
        }
    }

    public void save(ChatMessage m) throws SQLException {
        if (!enabled()) return;
        try (var c = scoped(m.chatId())) {
            lock(c, m.chatId());
            if (!capture(c, m.chatId())) {
                c.rollback();
                return;
            }
            try (var q =
                    c.prepareStatement(
                            """
INSERT INTO chat_messages(chat_id,message_id,thread_id,sender_user_id,sender_username,sender_display_name,sender_is_bot,direction,reply_to_message_id,message_type,text_content,telegram_date,edited_at)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(chat_id,message_id) DO UPDATE SET
text_content=excluded.text_content, message_type=excluded.message_type,
sender_username=COALESCE(excluded.sender_username,chat_messages.sender_username),
sender_display_name=COALESCE(excluded.sender_display_name,chat_messages.sender_display_name),
edited_at=excluded.edited_at,updated_at=now()
WHERE excluded.edited_at IS NOT NULL AND excluded.edited_at > COALESCE(chat_messages.edited_at,chat_messages.telegram_date)
""")) {
                Object[] values = {
                    m.chatId(),
                    m.messageId(),
                    m.threadId(),
                    m.userId(),
                    m.username(),
                    m.displayName(),
                    m.bot(),
                    m.direction(),
                    m.replyId(),
                    m.type(),
                    m.text(),
                    Timestamp.from(m.date()),
                    m.edited() == null ? null : Timestamp.from(m.edited())
                };
                for (int i = 0; i < values.length; i++) q.setObject(i + 1, values[i]);
                q.executeUpdate();
            }
            c.commit();
        }
    }

    private ChatMessage row(ResultSet r) throws SQLException {
        return new ChatMessage(
                r.getLong("chat_id"),
                r.getLong("message_id"),
                (Long) r.getObject("thread_id"),
                (Long) r.getObject("sender_user_id"),
                r.getString("sender_username"),
                r.getString("sender_display_name"),
                r.getBoolean("sender_is_bot"),
                r.getString("direction"),
                (Long) r.getObject("reply_to_message_id"),
                r.getString("message_type"),
                r.getString("text_content"),
                r.getTimestamp("telegram_date").toInstant(),
                r.getTimestamp("edited_at") == null
                        ? null
                        : r.getTimestamp("edited_at").toInstant());
    }

    public List<ChatMessage> recent(long chat, Long thread, int limit) throws SQLException {
        if (!enabled()) return List.of();
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit");
        try (var c = scoped(chat);
                var q =
                        c.prepareStatement(
                                "SELECT * FROM chat_messages WHERE chat_id=? AND thread_id IS NOT"
                                    + " DISTINCT FROM ? ORDER BY telegram_date DESC,message_id DESC"
                                    + " LIMIT ?")) {
            q.setLong(1, chat);
            q.setObject(2, thread);
            q.setInt(3, limit);
            try (var rs = q.executeQuery()) {
                List<ChatMessage> result = new ArrayList<>();
                while (rs.next()) result.add(row(rs));
                return result;
            }
        }
    }

    public Optional<ChatMessage> find(long chat, long id) throws SQLException {
        if (!enabled()) return Optional.empty();
        try (var c = scoped(chat);
                var q =
                        c.prepareStatement(
                                "SELECT * FROM chat_messages WHERE chat_id=? AND message_id=?")) {
            q.setLong(1, chat);
            q.setLong(2, id);
            try (var rs = q.executeQuery()) {
                return rs.next() ? Optional.of(row(rs)) : Optional.empty();
            }
        }
    }
}

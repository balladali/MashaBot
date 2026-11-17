# Stage 1 — отчёт реализации

Архитектура: централизованный ChatMemoryService (JDBC persistence и настройки),
ChatMessage mapper/model, ChatContextBuilder, MemoryCommands и MemoryProperties.
Выбран небольшой сервис без JPA и отдельной инфраструктуры агентов.

Telegram update → сохранение независимо от trigger → существующая очередь/выбор
обработчика → ChatContextBuilder → прежний ChatGptClient → TelegramDelivery →
сохранение реального Message ответа. Edited/channel updates только сохраняются.

Контекст: current → родители с chat scope → последние сообщения точного topic →
дедупликация → бюджет сообщений/символов → сортировка date/message_id → user/assistant.
Старые JVM-история, файловые профили и генерация сводок обходятся в режиме новой памяти.

Миграция V1: chat_messages с unique(chat_id,message_id), индексами
(chat_id,telegram_date DESC,message_id DESC) и
(chat_id,thread_id,telegram_date DESC,message_id DESC);
chat_memory_settings с PK(chat_id).

Изменённые и добавленные файлы:

- `pom.xml`
- `docker-compose.yml`
- `.env.example`
- `src/main/resources/application.yaml`
- `README.md`
- `src/test/java/ru/balladali/mashabot/BotApplicationTests.java`
- `src/main/java/ru/balladali/mashabot/telegram/Bot.java`
- `src/main/java/ru/balladali/mashabot/telegram/TelegramDelivery.java`
- `src/main/java/ru/balladali/mashabot/core/handlers/message/GptConversationHandler.java`
- `src/main/java/ru/balladali/mashabot/memory/ChatContextBuilder.java`
- `src/main/java/ru/balladali/mashabot/memory/ChatMemoryService.java`
- `src/main/java/ru/balladali/mashabot/memory/ChatMessage.java`
- `src/main/java/ru/balladali/mashabot/memory/MemoryCommands.java`
- `src/main/java/ru/balladali/mashabot/memory/MemoryProperties.java`
- `src/test/java/ru/balladali/mashabot/memory/ChatContextBuilderTest.java`
- `src/test/java/ru/balladali/mashabot/memory/ChatMemoryIntegrationTest.java`
- `src/test/java/ru/balladali/mashabot/memory/MemoryCommandsTest.java`
- `src/test/java/ru/balladali/mashabot/memory/MessageMappingTest.java`
- `src/main/resources/db/migration/V1__chat_memory.sql`

За пределами репозитория обновлён ../SERVER.md.

Ограничения: нет exactly-once отправки при повторе update; нет восстановления
успешного Telegram send при потере записи БД; runtime fail-open, но startup требует
БД при включённой памяти. Отсутствуют Micrometer, embeddings и новые сводки.
Фактический Telegram privacy mode требует проверки владельцем в BotFather.
Интеграционные тесты используют одноразовый PostgreSQL; рабочий бот не развёрнут.
Подробности эксплуатации, отказов и удаления истории — README.

Stage 2: PostgreSQL full-text + vector search с обязательным chat scope,
версионирование индексов, оценка hybrid ranking; отдельно outbox/update dedup.

Проверка: Maven verify — BUILD SUCCESS, 555 тестов, 0 failures, 0 errors,
0 skipped, включая 4 PostgreSQL integration tests и сквозной LLM/Telegram mock
сценарий. Лог: ../stage1-final.log. Одноразовый PostgreSQL остановлен и автоматически
удалён после проверки; SSH-туннель закрыт. Рабочий бот не развёртывался.

Уточнение настройки: MEMORY_DEFAULT_CHAT_ENABLED=false во всех конфигурациях.
Новые чаты не записываются до /memory_on; глобальные enabled/capture остаются true.
Ранее сохранённые индивидуальные разрешения не сбрасываются.

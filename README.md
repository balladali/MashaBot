# MashaBot

Запуск двух сервисов сразу:
- `masha-bot` (Java Telegram bot)
- `yt-video-analyzer` (Python FastAPI для анализа YouTube по субтитрам)

## Быстрый старт

```bash
cp .env.example .env
# заполни обязательные ключи в .env

docker compose up -d --build
```

Проверка:
```bash
docker compose ps
docker logs -f masha-bot
docker logs -f yt-video-analyzer
```

Остановить:
```bash
docker compose down
```

## Важные переменные

- `TG_BOT_TOKEN` — токен Telegram бота
- `OPENAI_API_KEY` — ключ для Java-бота (OpenRouter/OpenAI-совместимый)
- `OPENROUTER_API_KEY` — ключ для `yt-video-analyzer`
- `VIDEO_ANALYZER_URL` — URL Python-сервиса для Java-бота (по умолчанию `http://yt-video-analyzer:8000/analyze`)

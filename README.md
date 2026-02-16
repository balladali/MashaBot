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

### Voice mode (ElevenLabs)

- `VOICE_MODE=true|false` — включить/выключить голосовые ответы
- `VOICE_PROVIDER=elevenlabs|yandex` — провайдер озвучки (по умолчанию `elevenlabs`)
- `ELEVENLABS_API_KEY` — API key ElevenLabs
- `ELEVENLABS_VOICE_ID` — voice id ElevenLabs
- `ELEVENLABS_MODEL_ID` — модель ElevenLabs (по умолчанию `eleven_multilingual_v2`)

Ограничения стоимости:
- `VOICE_MAX_CHARS` — максимум символов для озвучки одного ответа
- `VOICE_DAILY_LIMIT` — максимум голосовых ответов в сутки
- `VOICE_MIN_INTERVAL_SEC` — минимальная пауза между голосовыми ответами
- `VOICE_SUMMARY_ON_LONG=true|false` — если ответ длиннее лимита, озвучивать короткую версию и отправлять полный текст сообщением

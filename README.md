# MashaBot

Маша — Telegram-бот на Java 21 и Spring Boot 4.0.3. Общается через LLM, запоминает контекст диалогов, анализирует YouTube с помощью отдельного сервиса, переводит суммы в рубли и считает простые выражения.

Бот получает сообщения через **long polling**: домен, HTTPS и webhook для Telegram не требуются. Для анализа видео Compose запускает второй сервис — [yt-video-analyzer](https://github.com/balladali/yt-video-analyzer).

## Содержание

- [Возможности и примеры](#возможности-и-примеры)
- [Запуск через Docker Compose](#запуск-через-docker-compose)
- [Сборка из исходников](#сборка-из-исходников)
- [Запуск без Docker](#запуск-без-docker)
- [Настройки](#настройки)
- [Память диалогов](#память-диалогов)
- [HTTP API](#http-api)
- [Обслуживание и диагностика](#обслуживание-и-диагностика)
- [Структура проекта](#структура-проекта)

## Возможности и примеры

| Возможность | Как использовать | Поведение |
| --- | --- | --- |
| Диалог с LLM | `Маша, объясни, что такое Docker` | Отвечает по-русски; характер и стиль задаются в `masha.persona` |
| Продолжение разговора | Ответить на сообщение бота через Reply | Подхватывает диалог без повторного обращения по имени |
| Контекст сообщения | Ответить на текст или подпись: `Маша, объясни это` | Добавляет текст и подпись исходного сообщения в запрос модели |
| Анализ YouTube | `Маша, выдели главные мысли https://youtu.be/VIDEO_ID` | Передаёт ссылку и задачу отдельному анализатору |
| Анализ ссылки из Reply | Ответить на сообщение со ссылкой: `Маша, проанализируй видео` | Берёт YouTube-ссылку из текста исходного сообщения |
| Калькулятор | `2*2`, `10 / 5`, `-2,5 × 4 =` | Считает локально, без LLM |
| Валюты в рубли | `100 USD`, `250 евро`, `2к долларов`, `100 белорусских рублей` | Автоматически распознаёт суммы в тексте и подписях, использует курсы ЦБ РФ |
| Старый режим p-bot | `Машка, привет` | Отдельный разговор через p-bot.ru с общим контекстом чата |
| Озвучка p-bot | `VOICE_MODE=true` и ключ Yandex Speech | Голосовой ответ в режиме «Машка» |

Обращение `Маша` или `Машка` должно стоять в начале текста; регистр не важен. Разделителями могут быть пробел, запятая, двоеточие и дефис. Обычный текст без обращения или Reply бот обрабатывает только при совпадении с калькулятором или валютами. Отдельного меню slash-команд в коде нет.

Приоритет обработчиков: **калькулятор → YouTube → LLM → p-bot → валюты**. Например, `Маша, сколько будет 100 USD?` попадёт в LLM, а отдельное `100 USD` — в конвертер.

### Ограничения возможностей

- **Калькулятор:** одно действие между двумя числами; операции `+`, `-`, `*`, `/`, `×`, `÷`, `−`, десятичная точка или запятая, необязательный `=` или `?` в конце. До 30 цифр в целой и дробной частях. Составные выражения и формулы внутри текста не вычисляются. `10/5` считается формулой. Деление на ноль сопровождается сообщением; бесконечные дроби округляются до 16 значащих цифр со знаком `≈`.
- **Валюты:** конвертация только в RUB, без LLM. Поддерживаются ISO-коды, символы, русские и английские названия, множители `к/k`, `кк/kk`, `тыс`, `млн`, `млрд`. Для неоднозначных названий указывайте страну или ISO-код. RUB пропускается, BYN конвертируется. Итог возможен только для валют, присутствующих в таблице ЦБ; таблица кэшируется на текущую дату по Москве. Это справочный курс ЦБ, а не курс покупки или продажи банка.
- **YouTube:** распознаются ссылки `youtube.com/watch?v=…`, `youtube.com/shorts/…` и `youtu.be/…` с `http://` или `https://`. Обращение `Маша` обязательно даже в Reply. Анализ основан на субтитрах во внешнем сервисе; просмотра кадров и распознавания загруженных видео в Java-боте нет. Результат зависит от доступности субтитров и YouTube.
- **Голос:** озвучка реализована только для p-bot; входящие голосовые сообщения не расшифровываются. Используется старый endpoint Yandex Speech, работоспособность зависит от доступности API и ключа.

## Запуск через Docker Compose

### Что понадобится

- Docker с плагином Compose и поддержкой BuildKit.
- Токен Telegram-бота, созданного через BotFather.
- Ключ LLM-провайдера для Java-бота и ключ OpenRouter для анализатора. При использовании OpenRouter в обоих сервисах можно указать один ключ в двух переменных.
- Доступ к Telegram, LLM API, ЦБ РФ, GitHub и YouTube.
- Файл cookies YouTube для предусмотренного Compose монтирования.

Все команды ниже выполняются из корня репозитория MashaBot, где лежат `pom.xml` и `docker-compose.yml`.

### 1. Подготовить конфигурацию

Linux/macOS:

```bash
cp .env.example .env
mkdir -p ./data/memory ./data/cookies
touch ./data/cookies/youtube-cookies.txt
```

PowerShell:

```powershell
Copy-Item .env.example .env
New-Item -ItemType Directory -Force ./data/memory, ./data/cookies
New-Item -ItemType File ./data/cookies/youtube-cookies.txt
```

Для нового окружения заполните `.env`:

```dotenv
TG_BOT_TOKEN=replace_with_telegram_token
OPENAI_API_KEY=replace_with_llm_key
CHATGPT_API_URL=https://openrouter.ai/api
CHATGPT_MODEL=x-ai/grok-4.1-fast
OPENROUTER_API_KEY=replace_with_openrouter_key
HOST_MASHA_MEMORY_DIR=./data/memory
HOST_YTDLP_COOKIES_FILE=./data/cookies/youtube-cookies.txt
```

Пустой cookies-файл позволяет подготовить bind mount, но не даёт авторизацию YouTube. Если YouTube требует вход, поместите в него свои cookies в формате Netscape. Путь должен указывать на существующий **файл**, иначе Docker может создать каталог. `.env`, cookies и содержимое памяти не следует коммитить или публиковать.

### 2. Запустить сервисы

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f --tail=100 masha-bot
```

В текущем Compose Java-бот запускается из готового образа **`balladali/masha-bot:master`**. Флаг `--build` собирает анализатор из ветки `main` внешнего репозитория; исходники Java он не собирает. Готовый образ может отличаться от текущего checkout. Для запуска изменённого кода используйте следующий раздел.

Compose публикует порты:

| Адрес хоста | Сервис | Назначение |
| --- | --- | --- |
| `2468` → `8080` | `masha-bot` | HTTP API Spring Boot |
| `8000` → `8000` | `yt-video-analyzer` | HTTP API анализатора |

Внутри Docker-сети бот обращается к `http://yt-video-analyzer:8000/analyze`. `depends_on` задаёт порядок запуска, но не ожидает готовности API анализатора. Порты по текущей конфигурации публикуются на всех интерфейсах хоста; ограничьте доступ к ним в своём окружении, если API нужны только локально.

### 3. Проверить в Telegram

Отправьте боту по очереди `2*2`, `Маша, привет` и `100 USD`. Для проверки видео отправьте обращение со ссылкой на ролик с доступными субтитрами.

В группе бот может обработать только сообщения, которые доставляет Telegram. Для автоматического калькулятора и валют в обычных сообщениях проверьте privacy mode в BotFather и права бота. Не запускайте две копии с одним токеном: long polling будет конфликтовать.

## Сборка из исходников

Dockerfile использует Maven 3.9.9 и JDK 21 для сборки, выполняет `clean verify` и запускает JAR на JRE 21.

Создайте в корне репозитория файл `compose.local.yaml`:

```yaml
services:
  masha-bot:
    image: masha-bot:local
    build:
      context: .
```

Соберите и запустите:

```bash
docker compose -f docker-compose.yml -f compose.local.yaml up -d --build
```

Для последующих команд управления этим окружением используйте те же два `-f`. Отдельная сборка образа без запуска:

```bash
docker build -t masha-bot:local .
```

## Запуск без Docker

Нужен JDK 21. В репозитории есть Maven Wrapper; установленный Maven можно использовать вместо него. Spring Boot **не загружает `.env` автоматически**: задайте переменные в оболочке или в конфигурации запуска IDE.

Минимальный пример для Linux/macOS:

```bash
export TG_BOT_TOKEN='replace_with_telegram_token'
export OPENAI_API_KEY='replace_with_llm_key'
export CHATGPT_API_URL='https://openrouter.ai/api'
export CHATGPT_MODEL='x-ai/grok-4.1-fast'
export MASHA_MEMORY_DIR="$PWD/data/memory"
./mvnw spring-boot:run
```

PowerShell:

```powershell
$env:TG_BOT_TOKEN = 'replace_with_telegram_token'
$env:OPENAI_API_KEY = 'replace_with_llm_key'
$env:CHATGPT_API_URL = 'https://openrouter.ai/api'
$env:CHATGPT_MODEL = 'x-ai/grok-4.1-fast'
$env:MASHA_MEMORY_DIR = Join-Path $PWD 'data/memory'
.\mvnw.cmd spring-boot:run
```

Без Docker HTTP-сервер по умолчанию слушает порт `8080`. Для YouTube отдельно запустите анализатор и задайте `VIDEO_ANALYZER_URL` (по умолчанию `http://127.0.0.1:8000/analyze`). Остальные возможности не требуют запущенного анализатора.

Сборка JAR и тестирование:

```bash
./mvnw -B -ntp clean verify
java -jar target/mashabot-1.0.0-SNAPSHOT.jar
```

В Windows используйте `.\mvnw.cmd -B -ntp clean verify`. Тесты используют локальные HTTP-серверы и mock Telegram/LLM; рабочие ключи им не нужны. Для запуска JAR переменные окружения должны оставаться заданными.

## Настройки

Источник настроек Java-приложения — [`application.yaml`](src/main/resources/application.yaml). Шаблон окружения — [`.env.example`](.env.example), передача переменных контейнерам — [`docker-compose.yml`](docker-compose.yml).

**Важно:** наличие переменной в `.env` само по себе не передаёт её контейнеру. Compose использует только переменные, перечисленные в его конфигурации. Для дополнительных параметров добавьте `environment` в локальный override.

### Telegram и LLM

| Переменная | Значение по умолчанию / назначение |
| --- | --- |
| `TG_BOT_TOKEN` | Обязательный токен Telegram |
| `OPENAI_API_KEY` | Ключ LLM для Java-бота |
| `CHATGPT_API_URL` | В Compose: `https://openrouter.ai/api`; при прямом запуске нужно задать |
| `CHATGPT_MODEL` | В Compose: `x-ai/grok-4.1-fast`; при прямом запуске нужно задать |
| `CHATGPT_REASONING_ENABLED` | `false`; явно передаётся как `reasoning.enabled`, поддержка зависит от провайдера и модели |
| `CHATGPT_MAX_TOKENS` | `1500`; положительный целочисленный лимит генерации обычного ответа |
| `CHATGPT_TIMEOUT_SEC` | `120`; read timeout, полный вызов ограничен этим значением + 10 секунд |
| `ERROR_CHAT_ID` | Пусто; необязательный чат для уведомлений с номером запроса и типом ошибки |
| `VOICE_MODE` | `false`; озвучка ответов p-bot |
| `YANDEX_SPEECH_API_KEY` | Ключ для озвучки |

Клиент сам добавляет `/v1/chat/completions` к `CHATGPT_API_URL`: не включайте этот суффикс в базовый URL. Это клиент совместимого Chat Completions API с дополнительным полем `reasoning`; совместимость конкретного провайдера следует проверять. Reasoning применяется также к сводкам памяти; их лимиты генерации отдельно фиксированы в коде: 350/450 токенов.

`TG_BOT_NAME` и `YANDEX_TRANSLATE_API_KEY` присутствуют в шаблоне/Compose, но не включают отдельные возможности: имя не участвует в маршрутизации, обработчика перевода в проекте нет.

### Очереди и память

| Переменная | Значение по умолчанию | Назначение |
| --- | --- | --- |
| `BOT_WORKERS` | `4` | Параллельные обработчики сообщений |
| `BOT_QUEUE_CAPACITY` | `64` | Общий лимит принятых сообщений, включая выполняемые |
| `BOT_DIALOG_QUEUE_CAPACITY` | `8` | Лимит на диалог |
| `BOT_SUMMARY_QUEUE_CAPACITY` | `16` | Лимит задач фоновых сводок |
| `GPT_MEMORY_MESSAGES` | `20` в Java | Число сообщений короткой истории, включая ответы |
| `GPT_MEMORY_TTL_MINUTES` | `30` | Срок короткой истории после последней активности |
| `GPT_SUMMARY_EVERY_USER_MESSAGES` | `100` | Частота формирования сводок |
| `MASHA_MEMORY_DIR` | `/opt/masha/memory` | Каталог профилей внутри приложения |
| `HOST_MASHA_MEMORY_DIR` | `/opt/masha/memory` | Каталог хоста, смонтированный Compose в `/opt/masha/memory` |

Текущий Compose **не передаёт** `GPT_MEMORY_MESSAGES`, `GPT_MEMORY_TTL_MINUTES`, `GPT_SUMMARY_EVERY_USER_MESSAGES` и `MASHA_MEMORY_DIR`. Поэтому `GPT_MEMORY_MESSAGES=100` из `.env.example` при таком запуске не действует: используется 20. Путь внутри контейнера остаётся `/opt/masha/memory`; путь хоста меняется через `HOST_MASHA_MEMORY_DIR`.

Пример дополнения `compose.local.yaml`:

```yaml
services:
  masha-bot:
    environment:
      GPT_MEMORY_MESSAGES: ${GPT_MEMORY_MESSAGES:-20}
      GPT_MEMORY_TTL_MINUTES: ${GPT_MEMORY_TTL_MINUTES:-30}
      GPT_SUMMARY_EVERY_USER_MESSAGES: ${GPT_SUMMARY_EVERY_USER_MESSAGES:-100}
      VIDEO_ANALYZER_TIMEOUT_SEC: ${VIDEO_ANALYZER_TIMEOUT_SEC:-90}
```

### Анализатор YouTube

Настройки ниже описывают передачу параметров из Compose внешнему сервису. Его реализация находится в отдельном репозитории.

| Переменная в `.env` | Значение по умолчанию / назначение |
| --- | --- |
| `OPENROUTER_API_KEY` | Ключ LLM анализатора |
| `VIDEO_ANALYZER_LLM_MODEL` | `openai/gpt-4o-mini` → `LLM_MODEL` |
| `VIDEO_ANALYZER_LLM_TEMPERATURE` | `0.35` → `LLM_TEMPERATURE` |
| `VIDEO_ANALYZER_URL` | В Compose: `http://yt-video-analyzer:8000/analyze` |
| `VIDEO_ANALYZER_TIMEOUT_SEC` | `90` в Java; требуется явно передать в Compose |
| `HOST_YTDLP_COOKIES_FILE` | `/opt/masha/cookies/youtube-cookies.txt` на хосте |
| `YTDLP_COOKIES_PATH` | `/app/cookies.txt` внутри анализатора |
| `YTDLP_MANUAL_MODE` | `true` |
| `YTDLP_INCLUDE_REGULAR_SUBS` | `false` |
| `YTDLP_FALLBACK_REGULAR_ON_EMPTY` | `true` |
| `YTDLP_FALLBACK_LANGS_ON_EMPTY` | `true` |
| `YTDLP_SUB_LANGS` | `ru,ru-orig` |
| `YTDLP_SUB_LANGS_FALLBACK` | `en,en-orig` |
| `YTDLP_EXTRACTOR_ARGS` | Пусто; дополнительные аргументы экстрактора |
| `YTDLP_KEEP_TMP` | `false` |
| `ANALYZE_CACHE_TTL_SEC` | `900` |
| `YTDLP_DEBUG` | `false` |
| `VIDEO_ANALYZER_LOG_LEVEL` | `INFO` → `LOG_LEVEL` |

Bind mount cookies в Compose фиксирован на `/app/cookies.txt`: при изменении `YTDLP_COOKIES_PATH` измените и целевой путь монтирования.

### Дополнительные настройки Java

При прямом запуске доступны `PORT` (по умолчанию `8080`), `PROXY_TYPE` (`NONE`, `HTTP`, `SOCKS`), `PROXY_HOST`, `PROXY_PORT`, `PROXY_USER`, `PROXY_PASSWORD`. Прокси используется общим OkHttp-клиентом Telegram-отправки, LLM и анализатора; это не глобальный прокси всех исходящих соединений приложения. Текущий Compose эти переменные не передаёт. При изменении `PORT` согласуйте контейнерный порт в `ports`.

## Память диалогов

Диалог изолирован по паре **чат + пользователь**, в том числе для участников одной группы. Короткая история хранится в RAM, очищается по TTL при следующем обращении и теряется после перезапуска. В историю попадают только успешно отправленные ответы.

После заданного числа сообщений пользователя отдельный фоновый обработчик создаёт сводку. Профили сохраняются как UTF-8 Markdown в `<MASHA_MEMORY_DIR>/<chatId>/<userId>.md` и подмешиваются в следующие запросы. Длинные профили повторно сжимаются моделью. Сохранённые профили переживают перезапуск при сохранении каталога хоста; ещё не сохранённая история и очереди — нет. Неудавшаяся сводка повторно планируется при следующем успешном обмене.

Память содержит сведения из переписки. Учитывайте это при доступе к файлам и резервном копировании. Встроенных команд просмотра или сброса памяти нет. У старого p-bot своя история последних обменов в RAM, общая для чата.

## HTTP API

Реализован один endpoint старого режима **p-bot**, без обращения к LLM и долговременной памяти:

```http
POST /api/v1/masha/answer
Content-Type: application/x-www-form-urlencoded
```

Параметры: `chatId` — ключ контекста, `message` — текст запроса. Ответ: `text/plain`.

Пример для Compose (в PowerShell используйте `curl.exe`):

```bash
curl -X POST http://localhost:2468/api/v1/masha/answer --data-urlencode 'chatId=demo' --data-urlencode 'message=Привет'
```

В контроллере нет авторизации. Запрос вызывает внешний p-bot и обновляет контекст указанного `chatId`. Для прямого запуска замените порт на `8080`. Отдельного health endpoint/Actuator в проекте нет; ответ 404 на `/` сам по себе не означает поломку бота.

## Обслуживание и диагностика

```bash
# Статус и последние логи
docker compose ps
docker compose logs --tail=100 masha-bot yt-video-analyzer

# Перезапустить без применения изменений .env
docker compose restart masha-bot

# Применить изменённое окружение пересозданием контейнера
docker compose up -d --force-recreate masha-bot

# Остановить сервисы
docker compose down
```

Для обновления готового Java-образа выполните `docker compose pull masha-bot`, затем `docker compose up -d masha-bot`. Перед обновлением сохраните конфигурацию, текущую версию образа и резервную копию каталога памяти. Сборка анализатора привязана к изменяемой ветке `main`; для воспроизводимого развёртывания закрепляйте версию отдельно. При локальной сборке используйте команды с override-файлом из раздела выше.

Сообщения одного диалога выполняются по порядку, разных диалогов — параллельно. При переполнении очереди бот пытается отправить просьбу повторить запрос. Во время обработки обновляет статус «печатает». Длинные ответы LLM и видео разбиваются под лимит Telegram.

Текстовые отправки повторяются не более трёх раз при явных ответах Telegram 429/5xx. Для 429 учитывается `retry_after`, если он не превышает 10 секунд. Неопределённые транспортные ошибки не повторяются автоматически, чтобы избежать дубликатов. Эти правила не распространяются на голосовые отправки.

В логах есть события `message_started` (`queue_ms`), `handler_selected`, `gpt_finished`, `video_finished`, `telegram_send`, `message_finished`, `queue_rejected`, `summary_failed`, `telegram_retry`. Поле `request`/MDC связывает этапы с update ID Telegram. В этих событиях не записываются тексты переписки, токены или тела ошибок API.

| Симптом | Что проверить |
| --- | --- |
| Бот не запускается | Токен, обязательные LLM-переменные, доступ к Telegram, логи контейнера |
| Конфликт long polling | Нет ли второй копии с тем же токеном |
| Бот молчит | Обращение в начале сообщения, Reply, privacy mode в группе, наличие подходящего обработчика |
| LLM возвращает ошибку | Ключ, доступность модели, базовый URL без `/v1/chat/completions`, совместимость `reasoning` |
| YouTube не анализируется | Логи анализатора, доступные субтитры, cookies, ключ OpenRouter, готовность сервиса и timeout |
| Память пропала | Bind mount, права на каталог, успешные сводки; короткая история после рестарта теряется |
| Изменения `.env` не действуют | Переменная перечислена в `environment`, контейнер пересоздан |
| Изменения Java не действуют | Используется локальная сборка, а не готовый образ `master` |
| Долгое ожидание | `queue_ms`, время внешних API, число обработчиков и лимиты очередей |

## Структура проекта

```text
src/main/java/ru/balladali/mashabot/
├── telegram/              # Long polling, отправка, typing status
├── core/config/           # Spring-конфигурация и параметры
├── core/handlers/message/ # Калькулятор, видео, LLM, p-bot, валюты
├── core/clients/          # Клиенты LLM, анализатора и ЦБ
├── core/services/         # Очереди, сводки памяти, синтез речи
└── web/                   # HTTP API старого p-bot
src/main/resources/application.yaml
src/test/                  # Тесты обработчиков, клиентов и инфраструктуры
docker-compose.yml         # Бот + внешний анализатор
Dockerfile                 # Сборка и runtime Java-бота
.env.example               # Шаблон окружения
```

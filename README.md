# Spending Limit Service (`spending.service`)

Сервис учета расходов клиентов и контроля месячных лимитов в USD. Обрабатывает транзакции в реальном времени (KZT, RUB, USD), конвертирует их по курсам Twelve Data, отслеживает месячные лимиты по категориям (`product`, `service`) и сохраняет историю операций.

---

## 1. Стек технологий

* **Java 21 LTS** (Records, Virtual Threads)
* **Spring Boot 4.1.1** (Web MVC, ProblemDetail RFC 9457)
* **PostgreSQL 15+ & jOOQ 3.19.16** (типобезопасный SQL, транзакции, `FOR UPDATE`)
* **Liquibase 4.29.2** (YAML change-sets)
* **Resilience4j 2.2.0** (CircuitBreaker, RateLimiter)
* **Тесты:** JUnit 5, AssertJ, Testcontainers (PostgreSQL), WireMock 3.13.0

---

## 2. Архитектура

Гексагональная архитектура (Ports & Adapters) с изоляцией доменной логики (DDD):

* `domain`: чистая бизнес-логика ([`ExpenseLimitDomainService`](file:///src/main/java/com/example/spending/service/domain/service/ExpenseLimitDomainService.java), [`CurrencyConversionService`](file:///src/main/java/com/example/spending/service/domain/service/CurrencyConversionService.java)), сущности и Value Objects (`AccountNumber`, `CurrencyCode`, `ExpenseCategory`). Никаких зависимостей от Spring или базы данных.
* `application`: юзкейсы ([`TransactionOperationService`](file:///src/main/java/com/example/spending/service/application/service/TransactionOperationService.java), [`ExpenseLimitService`](file:///src/main/java/com/example/spending/service/application/service/ExpenseLimitService.java)), оркестрация транзакций и восстановление отложенных платежей.
* `infrastructure`: входящие REST-контроллеры, jOOQ-репозитории, HTTP-клиент к Twelve Data и фоновые воркеры ([`UnprocessedTransactionWorker`](file:///src/main/java/com/example/spending/service/infrastructure/worker/UnprocessedTransactionWorker.java), [`CurrencyRateSyncWorker`](file:///src/main/java/com/example/spending/service/infrastructure/worker/CurrencyRateSyncWorker.java)).

---

## 3. Запуск и API

### Запуск через Docker Compose:
```bash
docker compose up --build
```
Сервис доступен на `http://localhost:8080`.
* **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Основные эндпоинты:
* `POST /api/transactions` — регистрация транзакции в реальном времени. Возвращает сохраненную транзакцию и флаг `limit_exceeded`.
* `POST /api/client/limits` — установка нового месячного лимита в USD (дата установления выставляется текущим системным временем).
* `GET /api/client/limits?account_from={acc}` — история всех лимитов клиента.
* `GET /api/client/transactions/exceeded?account_from={acc}` — список транзакций с `limit_exceeded = true`, дополненных данными лимита, действовавшего на момент каждой транзакции.

---

## 4. Инженерные решения

### 1. Почему jOOQ, а не Hibernate
Для этой задачи традиционный ORM — неподходящий инструмент:
* **Сложные агрегации и соединения:** поиск активного лимита на момент транзакции требует `JOIN LATERAL ... ORDER BY limit_datetime DESC LIMIT 1`. В JPQL/HQL это либо невозможно, либо порождает неэффективные подзапросы и N+1.
* **Специфика Postgres:** jOOQ дает прямой доступ к `INSERT ... ON CONFLICT DO NOTHING`, точечным `SELECT ... FOR UPDATE` и оконным функциям.
* **Специфика данных:** транзакции и лимиты — это неизменяемый финансовый лог (append-only ledger). Оверхед Hibernate на dirty checking, entity lifecycle и Session cache здесь полностью бесполезен.

### 2. Дефолтные лимиты не хранятся в БД
По ТЗ дефолтный лимит равен $1000.00 USD. Мы не создаем дефолтные строки в таблице `expense_limits`:
* Создание записи на каждый новый аккаунт и категорию привело бы к миллионам дубликатов $1000.
* Вместо этого дефолт подставляется в доменном сервисе на лету, если в БД не найдено ни одного пользовательского лимита.

### 3. Отсутствие внешних ключей (FK) на `account_from`
Банковский счет (условно) принадлежит отдельному микросервису, работающему на другом сервере со своей БД. Внешние ключи между сервисами физически невозможны и нарушают границы Bounded Context. На нашем уровне `account_from` валидируется регулярным выражением (10 цифр).

### 4. Часовой пояс — строго UTC (Требование 4)
Клиентские транзакции приходят с произвольными смещениями (например, `-05:00` или `+06:00`). Чтобы границы месяцев (`startOfMonth`, `startOfNextMonth`) считались детерминированно и одинаково для всех, все даты приводятся к **UTC (`ZoneOffset.UTC`)**.

### 5. DLQ и отслутствие потери данных
Если биржевой курс недоступен (биржа упала или лимит запросов исчерпан), а в кэше БД курса нет:
* **Транзакция принимается и сохраняется** в `unprocessed_transactions` со статусом `PENDING`. Никакие данные не теряются.
* Клиенту отдается `503 Service Unavailable` (RFC 9457 `ProblemDetail`) с полем `unprocessed_transaction_id` — сигнал о том, что транзакция принята, но будет обработана асинхронно.
* Фоновый воркер [`UnprocessedTransactionWorker`](file:///src/main/java/com/example/spending/service/infrastructure/worker/UnprocessedTransactionWorker.java) с распределенной блокировкой (lease duration) периодически опрашивает курс и завершает проведение накопившихся операций.

### 6. Каскадный DLQ: выбор Consistency over Availability
Если у счета хотя бы одна операция зависла в DLQ, **все последующие транзакции этого счета также направляются в DLQ**, а не проводятся сразу.
* *Почему:* если tx#1 на $1100 ушла в DLQ из-за курса, а следом пришла транзакция tx#2 на $50, мы не имеем права одобрить tx#2 со статусом `limit_exceeded = false`. Иначе, когда воркер проведет tx#1, хронология и статусы лимитов задним числом сломаются.
* Таким образом, выбираем Consistency over Availability: все зависимые транзакции паркуются и проводятся воркером строго FIFO.

### 7. Решение race condition призаписи лимитов
Для защиты от race conditions мы блокируем строку в `account_monthly_spending` через `SELECT ... FOR UPDATE` и все race conditions в большой транзакции сохранения остатков предотвращаются именно ей. Отсутствие записи в `account_monthly_spending` решается путем попытки создания записи с `ON CONFLICT DO NOTHING` перед попыткой прочитать с блокировкой (дедупликация - по `PK(account_from, expense_category, year_month)`).  Аномалий чтения у нас нет, поэтому достаточно просто READ_COMMITED.


### 8. Оптимизированный запрос превышенных транзакций
Для выборки превышенных транзакций с их историческим лимитом используется `JOIN LATERAL`:
```sql
SELECT tr.*, l.limit_sum, l.limit_datetime, l.currency_shortname
FROM transaction_records tr
JOIN LATERAL (
    SELECT limit_sum, limit_datetime, currency_shortname
    FROM expense_limits
    WHERE account_from = tr.account_from
      AND expense_category = tr.expense_category
      AND limit_datetime <= tr.datetime
    ORDER BY limit_datetime DESC
    LIMIT 1
) l ON true
WHERE tr.account_from = ? AND tr.limit_exceeded = true
ORDER BY tr.datetime ASC;
```
Фильтр по `account_from` и `limit_exceeded = true` применяется сразу, исключая full scan таблицы транзакций.

## 4.1. Логические решения

### 1. Округление дешевых валют
Для дешевых валют минимально допустимая сумма, например `0.000001 KZT` конвертируется в `2e-9 USD` чтобы не допустить конвертацию вв ноль и не дать бесконечно отправлять маленькие суммы, при этом не задавая минимального порога отправки (это скорее ответственность того сервиса, который занимается обработкой отправки самих транзакций) было реализовано округление CEILING, таким образом такя маленькая сумма приодится к минимально хранимому значению, то есть `0.000001 USD`.

---

## 5. Тестирование

71 тест (модульные + интеграционные с Testcontainers и WireMock):
```bash
mvn test
```
* `KeyScenarioE2EIntegrationTest` — сквозной тест сценария 1–13 января из ТЗ (случай 1).
* `BoundaryAndEdgeCasesIntegrationTest` — краевые условия лимитов ($1000.00 vs $1000.01), сброс месяца, дефолтный лимит (случай 2).
* `CurrencyAndResilienceIntegrationTest` — конвертация валют, fallback на `previous_close` в выходные, парковка в DLQ и восстановление воркером.
* `ConcurrencyIntegrationTest` — 10 параллельных потоков на один счет: ровно 5 транзакций проходят, ровно 5 превышают лимит, без гонок и дублей.

---

## 6. AI в проекте

* **Инструмент:** Antigravity CLI (AGY) на базе моделей Google Gemini.
* **MCP:** PostgreSQL MCP Server ([`.agents/mcp_config.json`](file:///.agents/mcp_config.json)) для инспекции схемы БД, индексов и плана запросов.
* **Скилл:** [`.agents/skills/add-currency-provider/SKILL.md`](file:///.agents/skills/add-currency-provider/SKILL.md) — шаблон для подключения нового провайдера котировок (порт, адаптер, DTO, Resilience4j, WireMock).
* **Что писалось вручную:** доменное ядро ([`ExpenseLimitDomainService`](file:///src/main/java/com/example/spending/service/domain/service/ExpenseLimitDomainService.java), [`DateTimeUtils`](file:///src/main/java/com/example/spending/service/domain/util/DateTimeUtils.java)), бизнес-инварианты, Liquibase-миграции, правила UTC.
* **Исправленные ошибки AI:**
  1. Верхний регистр в jOOQ генераторе (`"EXCHANGE_RATES"` ломал запросы в Postgres) — исправлен через `JooqConfig` (`RenderNameCase.LOWER`).
  2. Неэффективный подзапрос в `findExceededTransactionsWithLimit` — фильтр по счету перенесен внутрь `JOIN LATERAL`.
  3. Ошибка перехвата 503 в тестах — исправлено на `HttpServerErrorException` вместо `HttpClientErrorException`.
  4. Символы Twelve Data — AI не учел, что на бирже торгуются пары `USD/KZT` и `USD/RUB`, а не обратные; добавлена инверсия курсов (`1 / rate`), параметр `interval=1day` и перехват 4xx.

---

## 7. Оценка трудоёмкости

| Этап работ | Описание |        Оценка         |
| :--- | :--- |:---------------------:|
| 1. Архитектура и БД | Схема БД, Liquibase, jOOQ генерация, доменная модель (DDD) |          4 ч          |
| 2. REST API | Контроллеры, Bean Validation, ProblemDetail RFC 9457 |          3 ч          |
| 3. Интеграция с биржей | Twelve Data, кэш в БД, fallback на выходные, Resilience4j |          4 ч          |
| 4. Конкурентность и DLQ | Блокировки `FOR UPDATE`, таблица `unprocessed_transactions`, воркер |          4 ч          |
| 5. Тестирование | Unit-тесты, E2E (ТЗ п. 6), тесты конкурентности с Testcontainers |          6 ч          |
| 6. Документация и AI | AI-скилл, MCP, OpenAPI/Swagger, README.md |          2 ч          |
| **ИТОГО** | | **23 ч** около 3 дней |

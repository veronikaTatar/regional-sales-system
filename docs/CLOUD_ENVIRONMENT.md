# Подготовленное cloud-окружение

В `/workspace/tools` установлены полный Temurin JDK 21.0.9 (официальный архив проверен по SHA-256) и Maven 3.9.11 (проверен по SHA-512). Встроенный Java runtime образа не содержит полного комплекта разработки. Для команд сборки:

```bash
cd /workspace/regional-sales-system
source scripts/cloud-env.sh
cd backend
./mvnw -s /workspace/tools/maven/settings.xml test
```

Внешний Maven settings включает системный egress proxy этого cloud-окружения; системное хранилище доверенных сертификатов применяется без отключения TLS-проверки. Эти настройки не требуются на обычном ПК.

В корне создан игнорируемый `.env` с уникальными локальными паролями MySQL. Значения не добавляются в Git и документацию. Для запуска backend с этими настройками используйте отдельный терминал:

```bash
cd /workspace/regional-sales-system
source scripts/cloud-env.sh
docker compose up -d --wait
set -a
source .env
set +a
java -jar backend/target/regional-sales-backend-0.1.0-SNAPSHOT.jar
```

Другой терминал:

```bash
cd /workspace/regional-sales-system/frontend
npm run dev
```

Проверки: `curl -f http://localhost:8080/actuator/health` и `python scripts/smoke_auth.py`. Frontend — порт 5173. Процессы потребуется запускать заново после остановки машины; MySQL хранит данные в Docker volume. Google credentials отсутствуют. Администратор создаётся только после явной настройки bootstrap-переменных из README.

Каждая cloud-задача уже изолирована: используйте существующий checkout; не создавайте Git worktree без прямого запроса пользователя.

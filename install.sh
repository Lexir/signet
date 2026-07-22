#!/usr/bin/env bash
# Установщик Signet на чистый сервер: скачивает docker-compose.yml и конфиг nginx,
# спрашивает пароли для первичного запуска, собирает .env и поднимает сервис
# готовым образом из GHCR. Повторный запуск безопасен: существующий .env не трогается.
#
# Использование:
#   ./install.sh                  # интерактивно
#   GITHUB_TOKEN=ghp_… ./install.sh   # токен для приватного репозитория/пакета
set -euo pipefail

REPO="Lexir/signet"
BRANCH="${BRANCH:-main}"
FILES=("docker-compose.yml" "nginx/default.conf")

say()  { printf '\033[1;35m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mошибка:\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || fail "нужен docker (https://docs.docker.com/engine/install/)"
docker compose version >/dev/null 2>&1 || fail "нужен docker compose v2 (входит в docker-ce)"
command -v curl >/dev/null || fail "нужен curl"
command -v openssl >/dev/null || fail "нужен openssl (для генерации ключей)"

# --- Скачивание файлов -------------------------------------------------------
# Репозиторий может быть приватным: тогда raw-ссылки требуют GitHub-токен
# (Personal access token с доступом к репозиторию: scope `repo`).
fetch() {
  local path="$1" out="$2" url="https://raw.githubusercontent.com/${REPO}/${BRANCH}/${path}"
  local args=(-fsSL --create-dirs -o "$out")
  [ -n "${GITHUB_TOKEN:-}" ] && args+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
  curl "${args[@]}" "$url"
}

say "Скачиваю файлы из ${REPO}@${BRANCH}"
for f in "${FILES[@]}"; do
  if [ -e "$f" ]; then
    say "  $f уже есть — оставляю как есть"
    continue
  fi
  if ! fetch "$f" "$f" 2>/dev/null; then
    if [ -z "${GITHUB_TOKEN:-}" ]; then
      echo "Не удалось скачать $f — репозиторий, похоже, приватный."
      read -r -p "GitHub-токен (scope repo): " GITHUB_TOKEN
      [ -n "$GITHUB_TOKEN" ] || fail "без токена файлы не скачать"
      fetch "$f" "$f" || fail "не удалось скачать $f даже с токеном — проверьте доступ"
    else
      fail "не удалось скачать $f — проверьте токен и ветку ($BRANCH)"
    fi
  fi
  say "  $f ✓"
done

# --- Сбор .env ---------------------------------------------------------------
if [ -e .env ]; then
  say ".env уже существует — пароли не спрашиваю (удалите его, чтобы пересоздать)"
else
  say "Первичная настройка — учётные данные"

  read -r -p "Логин в панель [admin]: " DASH_USER
  DASH_USER="${DASH_USER:-admin}"

  while :; do
    read -r -s -p "Пароль в панель: " DASH_PASS; echo
    [ -n "$DASH_PASS" ] || { echo "пустой пароль нельзя"; continue; }
    read -r -s -p "Ещё раз: " DASH_PASS2; echo
    [ "$DASH_PASS" = "$DASH_PASS2" ] && break
    echo "не совпало, попробуйте снова"
  done

  read -r -s -p "Пароль PostgreSQL (Enter — сгенерировать): " DB_PASS; echo
  if [ -z "$DB_PASS" ]; then
    DB_PASS="$(openssl rand -hex 16)"
    say "  пароль БД сгенерирован"
  fi
  case "$DB_PASS" in (*'$'*) fail 'в пароле БД нельзя символ $ — compose трактует его как переменную';; esac

  # Ключ шифрования секретов в БД. Менять после первого запуска нельзя —
  # сохранённые пароли ящиков перестанут расшифровываться.
  SECRET_KEY="$(openssl rand -hex 32)"

  HTTPS_ANSWER=""
  read -r -p "HTTPS-сертификат уже настроен? [y/N]: " HTTPS_ANSWER
  COOKIE_SECURE=true
  case "$HTTPS_ANSWER" in ([yYдД]*) ;; (*) COOKIE_SECURE=false;; esac

  umask 077
  cat > .env <<ENV
# Сгенерировано install.sh $(date '+%Y-%m-%d %H:%M')
SPRING_PROFILES_ACTIVE=prod

DASHBOARD_USER=${DASH_USER}
DASHBOARD_PASS=${DASH_PASS}

DB_PASS=${DB_PASS}

# Ключ шифрования секретов в БД. НЕ МЕНЯТЬ после первого запуска.
# Забэкапьте отдельно от базы.
SETTINGS_SECRET_KEY=${SECRET_KEY}

# false — пока нет HTTPS (иначе session-cookie не установится по http).
# После включения TLS в nginx уберите эту строку.
COOKIE_SECURE=${COOKIE_SECURE}
ENV
  say ".env создан (права 600)"
  [ "$COOKIE_SECURE" = false ] \
    && say "⚠ вход будет по http — настройте TLS (см. nginx/default.conf) как можно скорее"
fi

# --- Образ и запуск ----------------------------------------------------------
IMAGE="ghcr.io/$(printf '%s' "$REPO" | tr '[:upper:]' '[:lower:]'):latest"
say "Скачиваю образ ${IMAGE}"
if ! docker pull "$IMAGE"; then
  echo "Пакет в GHCR приватный — нужен docker login (токен со scope read:packages):"
  read -r -p "GitHub-логин: " GH_USER
  docker login ghcr.io -u "$GH_USER" || fail "docker login не удался"
  docker pull "$IMAGE" || fail "образ не скачался — проверьте права токена (read:packages)"
fi

say "Запускаю"
docker compose up -d

say "Готово. Проверка:"
echo "  docker compose ps          # app должен стать healthy"
echo "  docker compose logs -f app"
echo
echo "Панель:  http://<адрес-сервера>/  (логин: из .env, DASHBOARD_USER)"
echo "Дальше:  /settings — завести ящик, Telegram-бота и AI-провайдера"

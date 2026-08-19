#!/usr/bin/env bash
# Установщик Signet на чистый сервер: скачивает docker-compose.yml, генерирует
# конфиг nginx, спрашивает пароли для первичного запуска, собирает .env и поднимает
# сервис готовым образом из GHCR. Повторный запуск безопасен: существующий .env не трогается.
#
# Репозиторий и образ публичны — токен не нужен. Переменная GITHUB_TOKEN нужна,
# только если доступ снова закроют (scope repo; для образа — docker login отдельно).
#
# Использование:
#   ./install.sh                      # интерактивно
#   GITHUB_TOKEN=ghp_… ./install.sh   # если репозиторий сделают приватным
set -euo pipefail

REPO="Lexir/signet"
BRANCH="${BRANCH:-main}"
# nginx/default.conf не качаем — скрипт генерирует его сам (http, а при вводе
# домена — сразу HTTPS), чтобы конфиг не приходилось править руками.
FILES=("docker-compose.yml")

# Домен и e-mail для TLS. Пусто — работаем по http.
DOMAIN=""
EMAIL=""

say()  { printf '\033[1;35m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mошибка:\033[0m %s\n' "$*" >&2; exit 1; }

# Генерация nginx/default.conf. Аргумент — домен; пусто = только http.
write_nginx() {
  mkdir -p nginx
  local domain="$1"
  if [ -z "$domain" ]; then
    cat > nginx/default.conf <<'NGINX'
# Сгенерировано install.sh — reverse proxy по http (ACME-проверка включена).
server {
    listen 80;
    server_name _;

    location /.well-known/acme-challenge/ { root /var/www/certbot; }

    client_max_body_size 25m;

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
NGINX
  else
    sed "s/__DOMAIN__/${domain}/g" > nginx/default.conf <<'NGINX'
# Сгенерировано install.sh — reverse proxy c TLS для __DOMAIN__.
server {
    listen 80;
    server_name __DOMAIN__;

    # ACME-проверка нужна и для продления сертификата.
    location /.well-known/acme-challenge/ { root /var/www/certbot; }

    location / { return 301 https://$host$request_uri; }
}

server {
    listen 443 ssl;
    http2 on;
    server_name __DOMAIN__;

    ssl_certificate     /etc/letsencrypt/live/__DOMAIN__/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/__DOMAIN__/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    client_max_body_size 25m;

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
NGINX
  fi
}

command -v docker >/dev/null || fail "нужен docker (https://docs.docker.com/engine/install/)"
docker compose version >/dev/null 2>&1 || fail "нужен docker compose v2 (входит в docker-ce)"
command -v curl >/dev/null || fail "нужен curl"
command -v openssl >/dev/null || fail "нужен openssl (для генерации ключей)"

# --- Скачивание файлов -------------------------------------------------------
# Репозиторий публичный — качается без токена. Если доступ закроют, raw-ссылки
# потребуют GitHub-токен (scope `repo`) — тогда сработает фолбэк ниже.
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

  # HTTPS: домен → сертификат выпустим автоматически. Пусто → работаем по http.
  echo "Let's Encrypt выдаёт сертификат только на домен (A-запись → этот сервер), не на IP."
  read -r -p "Домен для HTTPS (Enter — пропустить, работать по http): " DOMAIN
  DOMAIN="$(printf '%s' "$DOMAIN" | tr -d '[:space:]')"
  if [ -n "$DOMAIN" ]; then
    read -r -p "E-mail для Let's Encrypt (уведомления об истечении; Enter — без него): " EMAIL
    EMAIL="$(printf '%s' "$EMAIL" | tr -d '[:space:]')"
  fi
  # Стартуем всегда с COOKIE_SECURE=false; поднимем до true только после того,
  # как сертификат реально выпущен и nginx переключён на https.
  COOKIE_SECURE=false

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

# false — вход по http (иначе session-cookie не установится). install.sh
# сам поднимет до true, когда выпустит сертификат и переключит nginx на https.
COOKIE_SECURE=${COOKIE_SECURE}
ENV
  say ".env создан (права 600)"
fi

# --- nginx-конфиг ------------------------------------------------------------
# Всегда стартуем с http-конфига: nginx поднимается и отдаёт ACME-проверку.
# На https переключимся ниже, уже после того как сертификат реально выпущен.
[ -e nginx/default.conf ] || write_nginx ""

# --- Образ и запуск ----------------------------------------------------------
IMAGE="ghcr.io/$(printf '%s' "$REPO" | tr '[:upper:]' '[:lower:]'):latest"
say "Скачиваю образ ${IMAGE}"
if ! docker pull "$IMAGE"; then
  # Образ публичный и обычно тянется анонимно. Сюда попадаем, только если доступ
  # закрыли — тогда нужен docker login (токен со scope read:packages).
  echo "Не удалось скачать образ анонимно — возможно, пакет закрыли. Нужен docker login:"
  read -r -p "GitHub-логин: " GH_USER
  docker login ghcr.io -u "$GH_USER" || fail "docker login не удался"
  docker pull "$IMAGE" || fail "образ не скачался — проверьте права токена (read:packages)"
fi

say "Запускаю"
docker compose up -d

# --- Выпуск сертификата и переключение на HTTPS ------------------------------
# Только при первичной установке с доменом (при повторном запуске DOMAIN пуст).
TLS_ON=false
if [ -n "$DOMAIN" ]; then
  say "Жду, пока nginx поднимется на :80 (нужно для ACME-проверки)"
  sleep 3
  say "Выпускаю сертификат Let's Encrypt для ${DOMAIN}"
  CB_ARGS=(certonly --webroot -w /var/www/certbot -d "$DOMAIN" --agree-tos -n)
  if [ -n "$EMAIL" ]; then CB_ARGS+=(--email "$EMAIL"); else CB_ARGS+=(--register-unsafely-without-email); fi
  if docker compose run --rm certbot "${CB_ARGS[@]}"; then
    say "Сертификат получен — переключаю nginx на HTTPS"
    write_nginx "$DOMAIN"
    sed -i 's/^COOKIE_SECURE=.*/COOKIE_SECURE=true/' .env
    docker compose up -d          # app перечитает COOKIE_SECURE
    docker compose restart nginx  # подхватит https-конфиг
    TLS_ON=true
  else
    say "⚠ Сертификат выпустить не удалось (проверьте, что домен ${DOMAIN} указывает на этот сервер"
    say "  и порт 80 открыт снаружи). Пока остаётся http; повторить: docker compose run --rm certbot ..."
  fi
fi

say "Готово. Проверка:"
echo "  docker compose ps          # app должен стать healthy"
echo "  docker compose logs -f app"
echo
if [ "$TLS_ON" = true ]; then
  echo "Панель:  https://${DOMAIN}/  (логин: из .env, DASHBOARD_USER)"
else
  echo "Панель:  http://<адрес-сервера>/  (логин: из .env, DASHBOARD_USER)"
  [ -z "$DOMAIN" ] && echo "HTTPS:   перезапустите install.sh с доменом, либо см. nginx/default.conf"
fi
echo "Дальше:  /settings — завести ящик, Telegram-бота и AI-провайдера"

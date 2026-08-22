#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.production"
BASE_COMPOSE="$SCRIPT_DIR/compose.prod.yml"
LOCAL_DB_COMPOSE="$SCRIPT_DIR/compose.prod.local-db.yml"
STATE_DIR="$SCRIPT_DIR/.deploy"
BACKUP_DIR="$SCRIPT_DIR/backups"
TEMP_FILE=""

cleanup() {
  [[ -z "$TEMP_FILE" ]] || rm -f "$TEMP_FILE"
}
trap cleanup EXIT

info() { printf '\033[0;32m[OK]\033[0m %s\n' "$1"; }
warn() { printf '\033[0;33m[!]\033[0m %s\n' "$1"; }
fail() { printf '\033[0;31m[ERROR]\033[0m %s\n' "$1" >&2; exit 1; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Thiếu lệnh '$1'."
}

env_get() {
  local key="$1" line
  [[ -f "$ENV_FILE" ]] || return 0
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  line="${line#*=}"
  if [[ "$line" == \'*\' ]]; then
    line="${line#\'}"
    line="${line%\'}"
  fi
  printf '%s' "$line"
}

prompt() {
  local label="$1" default_value="${2:-}" answer
  read -r -p "$label${default_value:+ [$default_value]}: " answer
  printf '%s' "${answer:-$default_value}"
}

prompt_secret() {
  local label="$1" current_value="${2:-}" answer
  if [[ -n "$current_value" ]]; then
    read -r -s -p "$label [Enter để giữ giá trị hiện tại]: " answer
  else
    read -r -s -p "$label: " answer
  fi
  printf '\n' >&2
  printf '%s' "${answer:-$current_value}"
}

random_secret() {
  openssl rand -hex "${1:-32}"
}

write_env_line() {
  local file="$1" key="$2" value="$3"
  [[ "$value" != *$'\n'* && "$value" != *"'"* ]] || fail "$key chứa ký tự không được hỗ trợ (xuống dòng hoặc dấu nháy đơn)."
  printf "%s='%s'\n" "$key" "$value" >> "$file"
}

configure_env() {
  require_command openssl
  local db_choice db_mode db_url db_name db_user db_password db_schema
  local image tag bind_address backend_port frontend_url ws_origins same_site
  local redis_password minio_url minio_user minio_password jwt_secret health_url
  local smtp_host smtp_port smtp_user smtp_password mail_from

  printf '\nDatabase:\n  1) PostgreSQL Docker trên VPS\n  2) PostgreSQL managed (Supabase/Neon/RDS/...)\n'
  read -r -p "Chọn [1]: " db_choice
  db_choice="${db_choice:-1}"
  [[ "$db_choice" == "1" || "$db_choice" == "2" ]] || fail "Lựa chọn database không hợp lệ."

  image="$(prompt 'Backend image' "$(env_get BACKEND_IMAGE || true)")"
  image="${image:-ghcr.io/khanhnkq/connectcg-backend}"
  tag="$(prompt 'Image tag' "$(env_get BACKEND_TAG || true)")"
  tag="${tag:-latest}"
  bind_address="$(prompt 'Địa chỉ bind nội bộ (dùng reverse proxy)' "$(env_get SERVICE_BIND_ADDRESS || true)")"
  bind_address="${bind_address:-127.0.0.1}"
  backend_port="$(prompt 'Backend port trên VPS' "$(env_get BACKEND_PORT || true)")"
  backend_port="${backend_port:-8080}"
  frontend_url="$(prompt 'Frontend URL (HTTPS, không có dấu / cuối)' "$(env_get FRONTEND_URL || true)")"
  [[ "$frontend_url" == https://* ]] || warn "Production nên dùng URL HTTPS."
  ws_origins="$(prompt 'Allowed WebSocket/CORS origins (phân cách bằng dấu phẩy)' "$frontend_url")"
  same_site="$(prompt 'Cookie SameSite (Lax cùng root domain, None nếu khác domain)' "$(env_get AUTH_COOKIE_SAME_SITE || true)")"
  same_site="${same_site:-Lax}"

  if [[ "$db_choice" == "1" ]]; then
    db_mode="local"
    db_name="$(prompt 'Database name' "$(env_get DB_NAME || true)")"; db_name="${db_name:-connect_db}"
    db_user="$(prompt 'Database user' "$(env_get DB_USERNAME || true)")"; db_user="${db_user:-connect_user}"
    db_password="$(prompt_secret 'Database password' "$(env_get DB_PASSWORD || true)")"; db_password="${db_password:-$(random_secret 24)}"
    db_url="jdbc:postgresql://postgres:5432/$db_name"
    db_schema="public"
  else
    db_mode="managed"
    printf 'Supabase: dùng JDBC Session Pooler cổng 5432 + sslmode=require; không dùng Transaction Pooler 6543 cho Hibernate.\n'
    db_url="$(prompt 'JDBC URL' "$(env_get DB_URL || true)")"
    [[ "$db_url" == jdbc:postgresql://* ]] || fail "DB_URL phải bắt đầu bằng jdbc:postgresql://"
    db_name="$(prompt 'Database name' "$(env_get DB_NAME || true)")"; db_name="${db_name:-postgres}"
    db_user="$(prompt 'Database user' "$(env_get DB_USERNAME || true)")"
    db_password="$(prompt_secret 'Database password' "$(env_get DB_PASSWORD || true)")"
    db_schema="$(prompt 'Schema riêng của ứng dụng' "$(env_get DB_SCHEMA || true)")"; db_schema="${db_schema:-connectcg}"
  fi

  jwt_secret="$(prompt_secret 'JWT secret' "$(env_get JWT_SECRET || true)")"; jwt_secret="${jwt_secret:-$(random_secret 48)}"
  redis_password="$(prompt_secret 'Redis password' "$(env_get REDIS_PASSWORD || true)")"; redis_password="${redis_password:-$(random_secret 24)}"
  minio_user="$(prompt 'MinIO access key' "$(env_get MINIO_ACCESS_KEY || true)")"; minio_user="${minio_user:-connect-minio}"
  minio_password="$(prompt_secret 'MinIO secret key' "$(env_get MINIO_SECRET_KEY || true)")"; minio_password="${minio_password:-$(random_secret 32)}"
  minio_url="$(prompt 'MinIO public URL' "$(env_get MINIO_PUBLIC_URL || true)")"
  health_url="$(prompt 'Backend health-check URL' "http://127.0.0.1:${backend_port}/api/v1/auth/csrf")"
  smtp_host="$(prompt 'SMTP host' "$(env_get MAIL_HOST || true)")"
  smtp_port="$(prompt 'SMTP port' "$(env_get MAIL_PORT || true)")"; smtp_port="${smtp_port:-587}"
  smtp_user="$(prompt 'SMTP username' "$(env_get MAIL_USERNAME || true)")"
  smtp_password="$(prompt_secret 'SMTP password' "$(env_get MAIL_PASSWORD || true)")"
  mail_from="$(prompt 'Mail from address' "$(env_get MAIL_FROM_EMAIL || true)")"; mail_from="${mail_from:-noreply@example.com}"

  TEMP_FILE="$(mktemp "$SCRIPT_DIR/.env.production.tmp.XXXXXX")"
  write_env_line "$TEMP_FILE" DB_MODE "$db_mode"
  write_env_line "$TEMP_FILE" BACKEND_IMAGE "$image"
  write_env_line "$TEMP_FILE" BACKEND_TAG "$tag"
  write_env_line "$TEMP_FILE" SERVICE_BIND_ADDRESS "$bind_address"
  write_env_line "$TEMP_FILE" BACKEND_PORT "$backend_port"
  write_env_line "$TEMP_FILE" FRONTEND_URL "$frontend_url"
  write_env_line "$TEMP_FILE" WEBSOCKET_ALLOWED_ORIGINS "$ws_origins"
  write_env_line "$TEMP_FILE" SITE_URL "$frontend_url"
  write_env_line "$TEMP_FILE" DB_URL "$db_url"
  write_env_line "$TEMP_FILE" DB_NAME "$db_name"
  write_env_line "$TEMP_FILE" DB_USERNAME "$db_user"
  write_env_line "$TEMP_FILE" DB_PASSWORD "$db_password"
  write_env_line "$TEMP_FILE" DB_SCHEMA "$db_schema"
  write_env_line "$TEMP_FILE" JWT_SECRET "$jwt_secret"
  write_env_line "$TEMP_FILE" JWT_EXPIRATION "900000"
  write_env_line "$TEMP_FILE" REFRESH_TOKEN_LIFETIME "30d"
  write_env_line "$TEMP_FILE" AUTH_COOKIE_SECURE "true"
  write_env_line "$TEMP_FILE" AUTH_COOKIE_SAME_SITE "$same_site"
  write_env_line "$TEMP_FILE" REDIS_PASSWORD "$redis_password"
  write_env_line "$TEMP_FILE" RATE_LIMIT_ENABLED "true"
  write_env_line "$TEMP_FILE" MINIO_PUBLIC_URL "$minio_url"
  write_env_line "$TEMP_FILE" MINIO_ACCESS_KEY "$minio_user"
  write_env_line "$TEMP_FILE" MINIO_SECRET_KEY "$minio_password"
  write_env_line "$TEMP_FILE" MINIO_BUCKET "connect-media"
  write_env_line "$TEMP_FILE" MINIO_API_PORT "9000"
  write_env_line "$TEMP_FILE" MINIO_CONSOLE_PORT "9001"
  write_env_line "$TEMP_FILE" MAIL_HOST "$smtp_host"
  write_env_line "$TEMP_FILE" MAIL_PORT "$smtp_port"
  write_env_line "$TEMP_FILE" MAIL_USERNAME "$smtp_user"
  write_env_line "$TEMP_FILE" MAIL_PASSWORD "$smtp_password"
  write_env_line "$TEMP_FILE" MAIL_SMTP_AUTH "true"
  write_env_line "$TEMP_FILE" MAIL_SMTP_STARTTLS "true"
  write_env_line "$TEMP_FILE" MAIL_FROM_EMAIL "$mail_from"
  write_env_line "$TEMP_FILE" MAIL_FROM_NAME "Connect App"
  write_env_line "$TEMP_FILE" GEMINI_API_KEY "$(prompt_secret 'Gemini API key (có thể bỏ trống)' "$(env_get GEMINI_API_KEY || true)")"
  write_env_line "$TEMP_FILE" GEMINI_MODEL "gemini-1.5-flash"
  write_env_line "$TEMP_FILE" OPENROUTER_API_KEY "$(env_get OPENROUTER_API_KEY || true)"
  write_env_line "$TEMP_FILE" GOOGLE_CLIENT_ID "$(prompt 'Google client ID' "$(env_get GOOGLE_CLIENT_ID || true)")"
  write_env_line "$TEMP_FILE" GOOGLE_CLIENT_SECRET "$(prompt_secret 'Google client secret' "$(env_get GOOGLE_CLIENT_SECRET || true)")"
  write_env_line "$TEMP_FILE" FACEBOOK_CLIENT_ID "$(prompt 'Facebook client ID' "$(env_get FACEBOOK_CLIENT_ID || true)")"
  write_env_line "$TEMP_FILE" FACEBOOK_CLIENT_SECRET "$(prompt_secret 'Facebook client secret' "$(env_get FACEBOOK_CLIENT_SECRET || true)")"
  write_env_line "$TEMP_FILE" HEALTHCHECK_URL "$health_url"
  chmod 600 "$TEMP_FILE"
  mv "$TEMP_FILE" "$ENV_FILE"
  TEMP_FILE=""
  info "Đã tạo $ENV_FILE với quyền 600."
}

ensure_ready() {
  require_command docker
  docker compose version >/dev/null 2>&1 || fail "Docker Compose plugin chưa sẵn sàng."
  [[ -f "$ENV_FILE" ]] || fail "Chưa có .env.production. Chạy mục cấu hình trước."
  mkdir -p "$STATE_DIR" "$BACKUP_DIR"
}

current_tag() {
  if [[ -f "$STATE_DIR/current-tag" ]]; then
    tr -d '\n' < "$STATE_DIR/current-tag"
  else
    env_get BACKEND_TAG
  fi
}

compose() {
  local tag="${BACKEND_TAG_OVERRIDE:-$(current_tag)}"
  local files=(-f "$BASE_COMPOSE")
  if [[ "$(env_get DB_MODE)" == "local" ]]; then
    files+=(-f "$LOCAL_DB_COMPOSE")
  fi
  BACKEND_TAG="${tag:-latest}" docker compose --env-file "$ENV_FILE" "${files[@]}" "$@"
}

backup_database() {
  [[ "$(env_get DB_MODE)" == "local" ]] || { warn "Database managed: dùng snapshot/backup của nhà cung cấp trước migration."; return 0; }
  if ! compose ps --status running --services | grep -qx postgres; then
    warn "PostgreSQL chưa chạy; bỏ qua backup ở lần deploy đầu."
    return 0
  fi
  local backup_file="$BACKUP_DIR/postgres-$(date +%Y%m%d-%H%M%S).sql.gz"
  compose exec -T postgres pg_dump -U "$(env_get DB_USERNAME)" -d "$(env_get DB_NAME)" | gzip > "$backup_file"
  info "Đã backup database: $backup_file"
}

verify_backend() {
  require_command curl
  local url code attempt
  url="$(env_get HEALTHCHECK_URL)"
  for attempt in $(seq 1 20); do
    code="$(curl -sS -o /dev/null -w '%{http_code}' "$url" || true)"
    if [[ "$code" =~ ^(2|3)[0-9][0-9]$ ]]; then
      info "Health check thành công ($code): $url"
      return 0
    fi
    sleep 3
  done
  compose logs --tail 80 backend
  return 1
}

deploy_tag() {
  ensure_ready
  local previous desired
  previous="$(current_tag)"
  if [[ $# -gt 0 ]]; then
    desired="$1"
  else
    desired="$(prompt 'Tag cần deploy' "$previous")"
  fi
  [[ -n "$desired" ]] || desired="latest"
  BACKEND_TAG_OVERRIDE="$desired" compose config >/dev/null
  backup_database
  BACKEND_TAG_OVERRIDE="$desired" compose pull
  BACKEND_TAG_OVERRIDE="$desired" compose up -d --remove-orphans
  if verify_backend; then
    [[ -n "$previous" && "$previous" != "$desired" ]] && printf '%s\n' "$previous" > "$STATE_DIR/previous-tag"
    printf '%s\n' "$desired" > "$STATE_DIR/current-tag"
    info "Backend đang chạy tag $desired."
  else
    fail "Deploy không qua health check. Chọn Rollback để quay lại tag trước."
  fi
}

rollback() {
  ensure_ready
  local fallback target
  fallback="$(test -f "$STATE_DIR/previous-tag" && tr -d '\n' < "$STATE_DIR/previous-tag" || true)"
  target="$(prompt 'Tag rollback (nên dùng sha-...)' "$fallback")"
  [[ -n "$target" ]] || fail "Không có tag rollback."
  deploy_tag "$target"
}

menu() {
  while true; do
    printf '\nConnectCG Backend Deploy\n  1) Tạo/cập nhật environment\n  2) Pull và deploy/update\n  3) Trạng thái\n  4) Logs backend\n  5) Backup PostgreSQL local\n  6) Rollback image tag\n  0) Thoát\n'
    read -r -p 'Chọn: ' choice
    case "$choice" in
      1) configure_env ;;
      2) deploy_tag ;;
      3) ensure_ready; compose ps ;;
      4) ensure_ready; compose logs --tail 200 -f backend ;;
      5) ensure_ready; backup_database ;;
      6) rollback ;;
      0) return 0 ;;
      *) warn "Lựa chọn không hợp lệ." ;;
    esac
  done
}

menu

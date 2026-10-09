#!/usr/bin/env bash
set -euo pipefail

# 입력값
DEPLOY_ENV="${DEPLOY_ENV:?DEPLOY_ENV가 필요합니다 (dev/prod)}"
IMAGE="${IMAGE:?IMAGE가 필요합니다 (<registry>/kiri-server:<tag>)}"
NGINX_BUNDLE="${NGINX_BUNDLE:-}"

MEMORY_LIMIT="${MEMORY_LIMIT:-1200m}"
APP_JAVA_OPTS="${APP_JAVA_OPTS:--XX:MaxRAMPercentage=60.0}"

# 고정값
REGION="ap-northeast-2"
CONTAINER_NAME="kiri-server"
APP_PORT=8080

NGINX_DIR="/opt/kiri/nginx"
NGINX_CONTAINER="kiri-nginx"
NGINX_CHECK_NAME="kiri-nginx-check"
NGINX_PARAMETER_PATH="/kiri/${DEPLOY_ENV}/nginx"
NGINX_GID=101
LETSENCRYPT_DIR="/etc/letsencrypt"
CERTBOT_WEBROOT="/var/www/certbot"
CERTBOT_PROJECT="kiri-certbot"
SYSTEMD_DIR="/etc/systemd/system"

PARAMETER_PATH="/kiri/${DEPLOY_ENV}"
LOG_GROUP="/kiri/${DEPLOY_ENV}/app"

REGISTRY="${IMAGE%%/*}"
REPOSITORY="${IMAGE%:*}"
TAG="${IMAGE##*:}"

STOP_TIMEOUT=35
HEALTH_TIMEOUT=120
KEEP_IMAGES=3

export HOME="${HOME:-/root}"

ENV_FILE="$(mktemp)"
NGINX_STAGING=""
NGINX_BACKUP=""
trap 'rm -f "$ENV_FILE"; [[ -n "$NGINX_STAGING" ]] && rm -rf "$NGINX_STAGING"; [[ -n "$NGINX_BACKUP" ]] && rm -rf "$NGINX_BACKUP"' EXIT

log(){
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

LOCK_FILE="/var/lock/kiri-deploy.lock"
exec 9> "$LOCK_FILE"
if ! flock -w 600 9; then
  log "다른 배포가 10분 이상 진행 중입니다. 배포 중단"
  exit 1
fi

# 함수
write_env_file(){
  umask 077

  aws ssm get-parameters-by-path \
    --region "$REGION" \
    --path "$PARAMETER_PATH" \
    --with-decryption \
    --query 'Parameters[*].[Name, Value]' \
    --output text |
  while IFS=$'\t' read -r name value; do
    printf '%s=%s\n' "${name##*/}" "$value"
  done > "$ENV_FILE"

  echo "SPRING_PROFILES_ACTIVE=${DEPLOY_ENV}">>"$ENV_FILE"

  log "환경변수 $(wc -l < "$ENV_FILE")개 준비: $(cut -d= -f1 "$ENV_FILE" | tr '\n' ' ')"
}

run_container(){
  local image="$1"
  local tag="${image##*:}"

  docker run -d \
    --name "$CONTAINER_NAME" \
    --restart unless-stopped \
    -p "127.0.0.1:${APP_PORT}:${APP_PORT}" \
    --memory "$MEMORY_LIMIT" \
    --stop-timeout "$STOP_TIMEOUT" \
    --env-file "$ENV_FILE" \
    -e JAVA_TOOL_OPTIONS="$APP_JAVA_OPTS" \
    --log-driver awslogs \
    --log-opt awslogs-region="$REGION" \
    --log-opt awslogs-group="$LOG_GROUP" \
    --log-opt awslogs-stream="${CONTAINER_NAME}-${tag}" \
    "$image" > /dev/null
}

wait_for_healthy(){
  local elapsed=0

  while((elapsed<HEALTH_TIMEOUT));do
    if [[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null)" != "true" ]]; then
      log "컨테이너가 실행 중이 아님"
      return 1
    fi

    if curl -fsS --max-time 3 "http://127.0.0.1:${APP_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      log "헬스체크 성공 (${elapsed}초)"
      return 0
    fi

    sleep 5
    elapsed=$(( elapsed + 5 ))
  done

  log "헬스체크 시간 초과 (${HEALTH_TIMEOUT}초)"
  return 1
}

print_recent_logs(){
  log "---- 컨테이너 최근 로그 ----"
  docker logs --tail 50 "$CONTAINER_NAME" 2>&1 || true
  log "----------------------------"
}

cleanup_images(){
  local previous="$1"
  local extra=$(( KEEP_IMAGES - 2 ))

  docker images "$REPOSITORY" --filter dangling=false \
      --format '{{.CreatedAt}}\t{{.Repository}}:{{.Tag}}' |
    sort -r |
    cut -f2 |
    grep -vxF -e "$IMAGE" -e "${previous:-<none>}" |
    tail -n +$(( extra + 1 )) |
    xargs -r docker rmi > /dev/null 2>&1 || true

  docker image prune -f > /dev/null || true
}

remove_container(){
  if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    log "기존 컨테이너 정지 (최대 ${STOP_TIMEOUT}초 대기)"
    docker stop -t "$STOP_TIMEOUT" "$CONTAINER_NAME" > /dev/null || true
    docker rm "$CONTAINER_NAME" > /dev/null || true
  fi
}

ecr_login(){
  aws ecr get-login-password --region "$REGION" |
    docker login --username AWS --password-stdin "$REGISTRY" > /dev/null
}

pull_image(){
  log "이미지 pull"
  docker pull -q "$IMAGE" > /dev/null
}

current_image(){
  docker inspect -f '{{.Config.Image}}' "$CONTAINER_NAME" 2>/dev/null || true
}

rollback(){
  local previous="$1"

  print_recent_logs

  if [[ -z "$previous" || "$previous" == "$IMAGE" ]]; then
    log "롤백할 이전 이미지 없음. 배포 실패"
    exit 1
  fi

  log "이전 이미지로 롤백: ${previous##*:}"
  remove_container

  if run_container "$previous" && wait_for_healthy; then
    log "롤백 성공. 배포는 실패로 처리"
  else
    print_recent_logs
    log "롤백도 실패. 확인 필요"
  fi
  exit 1
}

nginx_running(){
  [[ "$(docker inspect -f '{{.State.Running}}' "$NGINX_CONTAINER" 2>/dev/null)" == "true" ]]
}

nginx_checksum(){
  (cd "$1" 2>/dev/null && cat compose.yml .env conf.d/kiri.conf auth/.htpasswd 2>/dev/null | sha256sum) || true
}

get_parameter(){
  aws ssm get-parameter \
    --region "$REGION" \
    --name "$1" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text
}

write_htpasswd(){
  local out="$1"
  local user password
  user="$(get_parameter "${NGINX_PARAMETER_PATH}/BASIC_AUTH_USER")"
  password="$(get_parameter "${NGINX_PARAMETER_PATH}/BASIC_AUTH_PASSWORD")"

  local existing="${NGINX_DIR}/auth/.htpasswd"
  if [[ -f "$existing" ]]; then
    local old_user old_hash salt
    IFS=: read -r old_user old_hash < "$existing"
    salt="$(cut -d'$' -f3 <<< "$old_hash")"

    if [[ "$old_user" == "$user" && "$(openssl passwd -6 -salt "$salt" -stdin <<< "$password")" == "$old_hash" ]]; then
      cp "$existing" "$out"
      return 0
    fi
  fi

  printf '%s:%s\n' "$user" "$(openssl passwd -6 -stdin <<< "$password")" > "$out"
  log "basic auth 계정 파일 갱신"
}

cert_domains(){
  sed -n 's#^[[:space:]]*ssl_certificate[[:space:]]\+/etc/letsencrypt/live/\([^/]\+\)/fullchain\.pem;.*#\1#p' "$1" | sort -u
}

run_certbot(){
  (cd "$NGINX_STAGING" && docker compose -p "$CERTBOT_PROJECT" run --rm -T "$@")
}

ensure_certificates(){
  local conf="$1"
  local domain

  local -a issue=(certonly --non-interactive --agree-tos --register-unsafely-without-email)

  for domain in $(cert_domains "$conf"); do
    if [[ -f "${LETSENCRYPT_DIR}/live/${domain}/fullchain.pem" ]]; then
      continue
    fi

    if nginx_running; then
      log "인증서 없음: ${domain}. webroot 방식으로 발급"
      install -d -m 755 "$CERTBOT_WEBROOT"
      if ! run_certbot certbot "${issue[@]}" --webroot -w "$CERTBOT_WEBROOT" -d "$domain"; then
        log "인증서 발급 실패: ${domain}. DNS가 이 서버를 가리키는지, 80 포트가 열려 있는지 확인"
        return 1
      fi
    else
      log "인증서 없음: ${domain}. standalone 방식으로 발급"
      if ! run_certbot --publish 80:80 certbot "${issue[@]}" --standalone -d "$domain"; then
        log "인증서 발급 실패: ${domain}. DNS가 이 서버를 가리키는지, 80 포트가 비어 있는지 확인"
        return 1
      fi
    fi

    log "인증서 발급 완료: ${domain}"
  done
}

install_renew_timer(){
  local new_service new_timer
  new_service="$(mktemp)"
  new_timer="$(mktemp)"

  cat > "$new_service" <<UNIT
[Unit]
Description=Renew Let's Encrypt certificates (certbot container)
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
WorkingDirectory=${NGINX_DIR}
ExecStart=/usr/bin/docker compose run --rm -T certbot renew --webroot -w ${CERTBOT_WEBROOT} --quiet
ExecStartPost=/usr/bin/docker exec ${NGINX_CONTAINER} nginx -s reload
UNIT

  cat > "$new_timer" <<'UNIT'
[Unit]
Description=Run certbot renew twice a day

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=1h
Persistent=true

[Install]
WantedBy=timers.target
UNIT

  if cmp -s "$new_service" "${SYSTEMD_DIR}/certbot-renew.service" \
    && cmp -s "$new_timer" "${SYSTEMD_DIR}/certbot-renew.timer"; then
    rm -f "$new_service" "$new_timer"
    return 0
  fi

  install -m 644 "$new_service" "${SYSTEMD_DIR}/certbot-renew.service"
  install -m 644 "$new_timer" "${SYSTEMD_DIR}/certbot-renew.timer"
  rm -f "$new_service" "$new_timer"

  systemctl daemon-reload
  systemctl enable --now certbot-renew.timer
  log "인증서 갱신 timer 등록"
}

install_nginx_files(){
  install -d -m 755 "$NGINX_DIR" "${NGINX_DIR}/conf.d" "${NGINX_DIR}/auth" /var/www/certbot \
  && install -m 644 "${NGINX_STAGING}/compose.yml" "${NGINX_STAGING}/.env" "$NGINX_DIR/" \
  && install -m 644 "${NGINX_STAGING}/conf.d/kiri.conf" "${NGINX_DIR}/conf.d/kiri.conf" \
  && install -m 640 -g "$NGINX_GID" "${NGINX_STAGING}/auth/.htpasswd" "${NGINX_DIR}/auth/.htpasswd"
}

activate_nginx(){
  local compose_changed="$1"

  if nginx_running && [[ "$compose_changed" == false ]]; then
    docker exec "$NGINX_CONTAINER" nginx -s reload || return 1
    log "nginx 설정 다시 읽기 완료"
  else
    (cd "$NGINX_DIR" && docker compose up -d) || return 1
    sleep 3
    if ! nginx_running; then
      log "nginx 컨테이너가 실행 직후 종료됨"
      docker logs --tail 20 "$NGINX_CONTAINER" 2>&1 || true
      return 1
    fi
    log "nginx 컨테이너 실행 완료"
  fi
}

backup_nginx(){
  NGINX_BACKUP="$(mktemp -d)"
  if [[ -d "$NGINX_DIR" ]]; then
    cp -a "${NGINX_DIR}/." "${NGINX_BACKUP}/"
  fi
}

restore_nginx(){
  if [[ -z "$(ls -A "$NGINX_BACKUP")" ]]; then
    log "첫 nginx 반영이라 복구할 이전 설정 없음"
    return 0
  fi

  cp -a "${NGINX_BACKUP}/." "${NGINX_DIR}/"

  if (cd "$NGINX_DIR" && docker compose up -d --force-recreate) && sleep 3 && nginx_running; then
    log "이전 nginx 설정으로 복구 완료"
  else
    log "nginx 복구도 실패. 서버에서 확인 필요"
  fi
}

apply_nginx(){
  if [[ -z "$NGINX_BUNDLE" ]]; then
    log "nginx 설정 묶음 없음. nginx 반영 건너뜀"
    return 0
  fi

  # 압축 풀기
  NGINX_STAGING="$(mktemp -d)"
  tar -xzf "$NGINX_BUNDLE" -C "$NGINX_STAGING"

  local conf="${NGINX_STAGING}/${DEPLOY_ENV}.conf"
  if [[ ! -f "$conf" ]]; then
    log "${DEPLOY_ENV}.conf 없음. nginx 반영 건너뜀"
    return 0
  fi

  # 서버 구조로 정리
  mkdir -p "${NGINX_STAGING}/conf.d"
  mv "$conf" "${NGINX_STAGING}/conf.d/kiri.conf"
  echo "DEPLOY_ENV=${DEPLOY_ENV}" > "${NGINX_STAGING}/.env"
  mkdir -p "${NGINX_STAGING}/auth"
  write_htpasswd "${NGINX_STAGING}/auth/.htpasswd"

  # 인증서 발급
  ensure_certificates "${NGINX_STAGING}/conf.d/kiri.conf"
  install_renew_timer

  # 설정 검사
  if [[ "$(nginx_checksum "$NGINX_STAGING")" == "$(nginx_checksum "$NGINX_DIR")" ]] && nginx_running; then
    log "nginx 설정 변경 없음"
    return 0
  fi

  log "nginx 새 설정 검사"
  docker rm -f "$NGINX_CHECK_NAME" > /dev/null 2>&1 || true
  if ! (cd "$NGINX_STAGING" && docker compose -p "$NGINX_CHECK_NAME" run --rm -T --name "$NGINX_CHECK_NAME" nginx nginx -t); then
    log "nginx 설정 검사 실패. 기존 nginx 설정을 유지하고 배포를 실패 처리"
    return 1
  fi

  local compose_changed=true
  cmp -s "${NGINX_STAGING}/compose.yml" "${NGINX_DIR}/compose.yml" \
    && cmp -s "${NGINX_STAGING}/.env" "${NGINX_DIR}/.env" \
    && compose_changed=false

  backup_nginx

  if ! install_nginx_files || ! activate_nginx "$compose_changed"; then
    log "nginx 반영 실패. 이전 nginx 설정으로 복구하고 배포를 실패 처리"
    restore_nginx
    return 1
  fi
}

# 배포
main(){
  log "배포 시작: env=${DEPLOY_ENV}, image=${TAG}, memory=${MEMORY_LIMIT}"

  ecr_login
  pull_image

  local previous
  previous="$(current_image)"
  log "이전 이미지: ${previous:-없음 (첫 배포)}"

  write_env_file
  remove_container
  log "새 컨테이너 실행"

  if ! run_container "$IMAGE" || ! wait_for_healthy; then
    rollback "$previous"
  fi

  apply_nginx

  cleanup_images "$previous"
  log "배포 성공: ${TAG}"
}

main
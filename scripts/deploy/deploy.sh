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
trap 'rm -f "$ENV_FILE"; [[ -n "$NGINX_STAGING" ]] && rm -rf "$NGINX_STAGING"' EXIT

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
    -p "${APP_PORT}:${APP_PORT}" \
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

    if curl -fsS --max-time 3 "http://localhost:${APP_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
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
  (cd "$1" 2>/dev/null && cat compose.yml .env conf.d/kiri.conf .htpasswd 2>/dev/null | sha256sum) || true
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

  local existing="${NGINX_DIR}/.htpasswd"
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
  write_htpasswd "${NGINX_STAGING}/.htpasswd"

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

  # 서버로 복사
  install -d -m 755 "$NGINX_DIR" "${NGINX_DIR}/conf.d" /var/www/certbot
  install -m 644 "${NGINX_STAGING}/compose.yml" "${NGINX_STAGING}/.env" "$NGINX_DIR/"
  install -m 644 "${NGINX_STAGING}/conf.d/kiri.conf" "${NGINX_DIR}/conf.d/kiri.conf"
  install -m 640 -g "$NGINX_GID" "${NGINX_STAGING}/.htpasswd" "${NGINX_DIR}/.htpasswd"

  # 반영
  if nginx_running && [[ "$compose_changed" == false ]]; then
    docker exec "$NGINX_CONTAINER" nginx -s reload
    log "nginx 설정 다시 읽기 완료"
  else
    (cd "$NGINX_DIR" && docker compose up -d)
    log "nginx 컨테이너 실행 완료"
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
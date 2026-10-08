#!/usr/bin/env bash
set -euo pipefail

# 입력값
DEPLOY_ENV="${DEPLOY_ENV:?DEPLOY_ENV가 필요합니다 (dev/prod)}"
IMAGE="${IMAGE:?IMAGE가 필요합니다 (<registry>/kiri-server:<tag>)}"

MEMORY_LIMIT="${MEMORY_LIMIT:-1200m}"
APP_JAVA_OPTS="${APP_JAVA_OPTS:--XX:MaxRAMPercentage=60.0}"

# 고정값
REGION="ap-northeast-2"
CONTAINER_NAME="kiri-server"
APP_PORT=8080

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
trap 'rm -f "$ENV_FILE"' EXIT

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
    --recursive \
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

  cleanup_images "$previous"
  log "배포 성공: ${TAG}"
}

main
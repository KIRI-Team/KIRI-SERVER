#!/usr/bin/env bash

set -euo pipefail

: "${DEPLOY_ENV:?}" "${IMAGE:?}" "${MEMORY_LIMIT:?}" "${APP_JAVA_OPTS:?}" "${AWS_REGION:?}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTANCE_NAME="kiri-${DEPLOY_ENV}-api"
WAIT_LIMIT=1200

find_instance(){
  local ids count

  ids=$(aws ec2 describe-instances \
      --filters "Name=tag:Name,Values=${INSTANCE_NAME}" "Name=instance-state-name,Values=running" \
      --query 'Reservations[].Instances[].InstanceId' \
      --output text)

  count=$(wc -w <<< "$ids")
  if [[ "$count" -ne 1 ]]; then
    echo "::error::배포 대상이 1대가 아닙니다. (${count}대): ${INSTANCE_NAME}" >&2
    exit 1
  fi

  echo "$ids"
}

send_deploy_command(){
  local instance_id="$1"
  local script_b64 remote_cmd

  script_b64=$(base64 -w0 "${SCRIPT_DIR}/deploy.sh")

  remote_cmd=$(cat <<EOF
script=\$(mktemp /tmp/kiri-deploy.XXXXXX)
echo '${script_b64}' | base64 -d > "\$script"
DEPLOY_ENV='${DEPLOY_ENV}' IMAGE='${IMAGE}' MEMORY_LIMIT='${MEMORY_LIMIT}' APP_JAVA_OPTS='${APP_JAVA_OPTS}' bash "\$script"; rc=\$?
rm -f "\$script"
exit \$rc
EOF
)

  aws ssm send-command \
      --instance-ids "$instance_id" \
      --document-name AWS-RunShellScript \
      --comment "deploy ${IMAGE##*:}" \
      --parameters "$(jq -n --arg cmd "$remote_cmd" --arg timeout "$WAIT_LIMIT" '{commands: [$cmd], executionTimeout: [$timeout]}')" \
      --query 'Command.CommandId' \
      --output text
}

wait_for_result(){
  local command_id="$1" instance_id="$2"
  local output status elapsed=0

  while (( elapsed < WAIT_LIMIT )); do
    if output=$(aws ssm get-command-invocation \
          --command-id "$command_id" --instance-id "$instance_id" \
          --query 'Status' --output text 2>&1); then
      status="$output"
    elif [[ "$output" == *InvocationDoesNotExist* ]]; then
      status="Pending"
    else
      echo "::error::SSM 실행 결과 조회 실패: ${output}" >&2
      echo "QueryFailed"
      return
    fi

    case "$status" in
      Pending|InProgress|Delayed)
        sleep 5
        elapsed=$(( elapsed + 5 ))
        ;;
      *)
        echo "$status"
        return
        ;;
    esac
  done

  echo "::error::${WAIT_LIMIT}초 동안 배포가 끝나지 않았습니다. AWS 콘솔 › Systems Manager › Run Command에서 확인" >&2
  echo "TimedOut"
}

print_output(){
  local command_id="$1" instance_id="$2"

  echo "::group::배포 스크립트 출력"
  aws ssm get-command-invocation \
    --command-id "$command_id" --instance-id "$instance_id" \
    --query '[StandardOutputContent, StandardErrorContent]' --output text
  echo "::endgroup::"
}

main(){
  local instance_id command_id status

  instance_id=$(find_instance)
  echo "배포 대상: ${instance_id} (${INSTANCE_NAME})"

  command_id=$(send_deploy_command "$instance_id")
  echo "SSM CommandId: ${command_id}"

  status=$(wait_for_result "$command_id" "$instance_id")

  if [[ "$status" != "QueryFailed" ]]; then
    print_output "$command_id" "$instance_id"
  fi

  echo "최종 상태: ${status}"
  if [[ "$status" != "Success" ]]; then
    echo "::error::배포 실패 (${status}). 롤백 여부는 위 출력에서 확인" >&2
    exit 1
  fi
}

main

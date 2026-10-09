#!/usr/bin/env bash
# 응답을 마치기 전에 포맷과 구조 규칙을 검사. 실패하면 exit 2로 Claude가 이어서 고치게 함

input=$(cat)

# 이 hook 때문에 이미 한 번 이어서 작업한 경우 다시 막지 않음 (무한 반복 방지)
if printf '%s' "$input" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
    exit 0
fi

# Java 코드나 빌드 설정을 바꾸지 않았으면 검사하지 않음
if [ -z "$(git status --porcelain -- '*.java' 'build.gradle')" ]; then
    exit 0
fi

. "$(dirname "$0")/java-env.sh" || exit 0

# 전체 테스트는 DB가 필요해서 여기서는 DB 없이 도는 검사만 실행
if ! output=$(./gradlew spotlessCheck test --tests "com.kiri.server.ArchitectureTest" --console=plain 2>&1); then
    {
        echo "spotlessCheck 또는 ArchitectureTest가 실패했습니다. 원인을 고친 뒤 마무리하세요."
        echo "(포맷 문제는 ./gradlew spotlessApply 로 수정)"
        printf '%s\n' "$output" | grep -E "FAILED|was violated|violations|Violations|spotlessApply" | head -30
    } >&2
    exit 2
fi
exit 0

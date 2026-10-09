#!/usr/bin/env bash
# Edit/Write 직후, 수정한 Java 파일 하나만 Spotless로 포맷

input=$(cat)

# jq 없이 tool_input.file_path 추출 (Windows 경로의 \\ 는 / 로 변환)
file=$(printf '%s' "$input" \
    | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' \
    | head -1 \
    | sed 's/\\\\/\//g')

case "$file" in
    *.java) ;;
    *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

. "$(dirname "$0")/java-env.sh" || exit 0

# spotlessIdeHook: 지정한 파일 하나만 포맷 (전체 실행보다 빠름)
if ! ./gradlew -q spotlessApply -PspotlessIdeHook="$file" >/dev/null 2>&1; then
    echo "[hook] Spotless 포맷 실패: $file" >&2
fi
exit 0

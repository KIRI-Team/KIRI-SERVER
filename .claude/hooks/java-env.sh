# Gradle 실행에 필요한 JDK 25를 찾음. 못 찾으면 hook만 건너뛰고 작업은 막지 않음
# 순서: JAVA_HOME → PATH의 java → macOS java_home → IntelliJ가 받은 JDK(~/.jdks)
if [ -z "$JAVA_HOME" ] && ! command -v java >/dev/null 2>&1; then
    if [ -x /usr/libexec/java_home ] && detected=$(/usr/libexec/java_home -v 25 2>/dev/null); then
        export JAVA_HOME="$detected"
    else
        detected=$(ls -d "$HOME"/.jdks/*-25* 2>/dev/null | sort -V | tail -1)
        if [ -n "$detected" ]; then
            export JAVA_HOME="$detected"
        fi
    fi
fi

if [ -z "$JAVA_HOME" ] && ! command -v java >/dev/null 2>&1; then
    echo "[hook] JDK 25를 찾지 못해 검사를 건너뜁니다. docs/local-setup.md의 Claude Code hooks 항목을 참고하세요." >&2
    return 1
fi

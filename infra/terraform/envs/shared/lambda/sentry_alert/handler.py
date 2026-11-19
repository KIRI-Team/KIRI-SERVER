"""Sentry Internal Integration 웹훅을 받아 Discord 채널로 알림을 보냄

- Sentry-Hook-Signature(HMAC-SHA256)로 Sentry에서 온 요청인지 검증
- event_alert(알림 규칙 발동), issue(created) 리소스만 전송하고 나머지는 무시
- 비밀 값은 SSM Parameter Store에서 읽고 컨테이너가 살아있는 동안 캐시
"""

import base64
import hashlib
import hmac
import json
import logging
import os
import urllib.error
import urllib.request

import boto3

logger = logging.getLogger()
logger.setLevel(logging.INFO)

ssm = boto3.client("ssm")

DISCORD_WEBHOOK_PARAM = os.environ["DISCORD_WEBHOOK_PARAM"]
SENTRY_CLIENT_SECRET_PARAM = os.environ["SENTRY_CLIENT_SECRET_PARAM"]

LEVEL_COLORS = {
    "fatal": 0x8B0000,
    "error": 0xE03E2F,
    "warning": 0xF2A33A,
    "info": 0x3B82F6,
    "debug": 0x9CA3AF,
}

_params = {}


def _get_param(name):
    if name not in _params:
        _params[name] = ssm.get_parameter(Name=name, WithDecryption=True)["Parameter"]["Value"]
    return _params[name]


def _response(status, message):
    return {"statusCode": status, "body": json.dumps({"message": message})}


def _verify_signature(body, signature):
    secret = _get_param(SENTRY_CLIENT_SECRET_PARAM)
    expected = hmac.new(secret.encode(), body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature or "")


def _tag(event, key):
    for tag in event.get("tags") or []:
        if isinstance(tag, (list, tuple)) and len(tag) == 2 and tag[0] == key:
            return tag[1]
    return None


def _truncate(text, limit):
    text = text or ""
    return text if len(text) <= limit else text[: limit - 1] + "…"


def _build_embed(resource, payload):
    data = payload.get("data") or {}

    if resource == "event_alert":
        event = data.get("event") or {}
        title = event.get("title")
        url = event.get("web_url") or event.get("issue_url")
        culprit = event.get("culprit")
        level = event.get("level")
        environment = event.get("environment") or _tag(event, "environment")
        rule = data.get("triggered_rule")
    elif resource == "issue" and payload.get("action") == "created":
        issue = data.get("issue") or {}
        title = issue.get("title")
        url = issue.get("web_url") or issue.get("permalink")
        culprit = issue.get("culprit")
        level = issue.get("level")
        environment = None
        rule = "새 이슈"
    else:
        return None

    fields = [
        {"name": "Environment", "value": environment or "-", "inline": True},
        {"name": "Level", "value": level or "-", "inline": True},
    ]
    if rule:
        fields.append({"name": "Rule", "value": _truncate(rule, 1024), "inline": False})

    return {
        "title": _truncate(title or "Sentry 알림", 256),
        "url": url,
        "description": _truncate(culprit, 4096),
        "color": LEVEL_COLORS.get(level, LEVEL_COLORS["error"]),
        "fields": fields,
    }


def _send_discord(embed):
    request = urllib.request.Request(
        _get_param(DISCORD_WEBHOOK_PARAM),
        data=json.dumps({"embeds": [embed]}).encode(),
        headers={
            "Content-Type": "application/json",
            # 기본 Python-urllib User-Agent는 Discord(Cloudflare)에서 차단될 수 있음
            "User-Agent": "kiri-sentry-alert/1.0",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return response.status


def lambda_handler(event, context):
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}

    body = event.get("body") or ""
    body = base64.b64decode(body) if event.get("isBase64Encoded") else body.encode()

    if not _verify_signature(body, headers.get("sentry-hook-signature")):
        logger.warning("서명 검증 실패")
        return _response(401, "invalid signature")

    resource = headers.get("sentry-hook-resource")
    payload = json.loads(body)

    embed = _build_embed(resource, payload)
    if embed is None:
        logger.info("무시한 웹훅: resource=%s action=%s", resource, payload.get("action"))
        return _response(200, "ignored")

    try:
        status = _send_discord(embed)
    except urllib.error.HTTPError as e:
        logger.error("Discord 전송 실패: %s %s", e.code, e.read().decode(errors="replace"))
        return _response(502, "discord error")
    except urllib.error.URLError as e:
        logger.error("Discord 전송 실패: %s", e.reason)
        return _response(502, "discord error")

    logger.info("Discord 전송 완료: resource=%s status=%s", resource, status)
    return _response(200, "sent")

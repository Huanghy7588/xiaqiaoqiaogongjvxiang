#!/usr/bin/env bash
# purge_cdn.sh - 刷新 jsDelivr CDN 缓存
#
# 用法:
#   ./purge_cdn.sh              # 只 purge @latest
#   ./purge_cdn.sh v1.0.9       # purge @latest + @v1.0.9
#
# 发版流程的最后一步：push 代码 + tag 后跑这个脚本，
# 让旧版本 App 立即检测到新版本更新。
#
# 原理：jsDelivr 的 @latest 和 @<tag> 解析即时，但文件内容有 CDN 缓存。
#       query string 不能绕过缓存（已验证），只能通过 purge API 强制刷新。

set -e

REPO="Huanghy7588/xiaqiaoqiaogongjvxiang"
VERSION="${1:-}"

# 要 purge 的文件（发版时变化的两个文件）
FILES=("update.json" "app-release.apk")

echo "=== jsDelivr CDN Purge ==="
echo "仓库: $REPO"
[ -n "$VERSION" ] && echo "版本: $VERSION"
echo

# 构建 purge URL 列表
declare -a PURGE_URLS=()
for f in "${FILES[@]}"; do
    PURGE_URLS+=("https://purge.jsdelivr.net/gh/${REPO}@latest/${f}")
done
if [ -n "$VERSION" ]; then
    for f in "${FILES[@]}"; do
        PURGE_URLS+=("https://purge.jsdelivr.net/gh/${REPO}@${VERSION}/${f}")
    done
fi

# 执行 purge
SUCCESS=0
FAIL=0
for url in "${PURGE_URLS[@]}"; do
    echo -n "  purge: ${url#https://purge.jsdelivr.net/gh/} ... "
    RESP=$(curl -s --max-time 30 "$url" 2>&1 || echo "CURL_ERROR")
    if echo "$RESP" | grep -q '"status": "finished"'; then
        echo "OK"
        SUCCESS=$((SUCCESS + 1))
    else
        echo "FAIL"
        echo "    响应: $RESP"
        FAIL=$((FAIL + 1))
    fi
done

echo
echo "=== 验证（加时间戳防缓存）==="
TS=$(date +%s%N)
verify_url() {
    local ver="$1"
    local file="$2"
    local url="https://cdn.jsdelivr.net/gh/${REPO}@${ver}/${file}?nocache=${TS}"
    echo -n "  @${ver}/${file} -> "
    if [ "$file" = "update.json" ]; then
        curl -s --max-time 15 "$url" 2>/dev/null | grep -oE '"versionCode": [0-9]+|"versionName": "[^"]*"' | tr '\n' ' '
        echo
    else
        # apk 是二进制，只看能否拿到 + 大小
        SIZE=$(curl -sI --max-time 15 "$url" 2>/dev/null | grep -i content-length | awk '{print $2}' | tr -d '\r')
        echo "${SIZE:-未知} bytes"
    fi
}

verify_url "latest" "update.json"
[ -n "$VERSION" ] && verify_url "$VERSION" "update.json"
[ -n "$VERSION" ] && verify_url "$VERSION" "app-release.apk"

echo
echo "完成: 成功 $SUCCESS / 失败 $FAIL"
[ "$FAIL" -gt 0 ] && exit 1
exit 0

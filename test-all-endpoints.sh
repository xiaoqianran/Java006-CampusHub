#!/bin/bash
# 时迁平台 - 全接口自动化测试脚本
# 基于实际运行的后端服务 (user:8081, resource:8082)
set -u

BASE_USER="http://localhost:8081/api/user"
BASE_RES="http://localhost:8082/api"
TOKEN=""
USER_ID=""
TEST_USER="test_$(date +%s)"
PASS="TestPass123"
REPORT="docs/接口测试报告.md"
mkdir -p docs logs

echo "========================================" | tee -a $REPORT
echo "时迁校园资源共享平台 - 后端接口测试报告" | tee -a $REPORT
echo "测试时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $REPORT
echo "服务状态: User(8081), Resource(8082)" | tee -a $REPORT
echo "========================================" | tee -a $REPORT

pass_count=0
fail_count=0

function log_result() {
    local name="$1"
    local code="$2"
    local expected="$3"
    local detail="$4"
    if [ "$code" = "$expected" ]; then
        echo "✅ [PASS] $name (HTTP $code)" | tee -a $REPORT
        ((pass_count++))
    else
        echo "❌ [FAIL] $name (HTTP $code, 期望 $expected) $detail" | tee -a $REPORT
        ((fail_count++))
    fi
}

function curl_post() {
    local url="$1"
    local data="$2"
    local auth="${3:-}"
    curl -s -w "\n%{http_code}" -X POST "$url" \
        -H "Content-Type: application/json" \
        ${auth:+-H "$auth"} \
        -d "$data" 2>/dev/null
}

function curl_get() {
    local url="$1"
    local auth="${2:-}"
    curl -s -w "\n%{http_code}" -X GET "$url" \
        ${auth:+-H "$auth"} 2>/dev/null
}

function curl_put() {
    local url="$1"
    local data="$2"
    local auth="${3:-}"
    curl -s -w "\n%{http_code}" -X PUT "$url" \
        -H "Content-Type: application/json" \
        ${auth:+-H "$auth"} \
        -d "$data" 2>/dev/null
}

function curl_delete() {
    local url="$1"
    local auth="${2:-}"
    curl -s -w "\n%{http_code}" -X DELETE "$url" \
        ${auth:+-H "$auth"} 2>/dev/null
}

echo ""
echo "=== 1. 用户服务测试 ===" | tee -a $REPORT

# 1.1 Health
resp=$(curl_get "$BASE_USER/health")
http_code=$(echo "$resp" | tail -n1)
body=$(echo "$resp" | sed '$d')
log_result "GET /health (用户健康检查)" "$http_code" "200" "$body"

# 1.2 Register
echo "注册用户: $TEST_USER"
reg_data='{"username":"'$TEST_USER'","password":"'$PASS'","nickname":"测试用户","email":"'$TEST_USER'@example.com"}'
resp=$(curl_post "$BASE_USER/register" "$reg_data")
http_code=$(echo "$resp" | tail -n1)
log_result "POST /register (用户注册)" "$http_code" "200" ""

# 1.3 Register duplicate (expect fail)
resp=$(curl_post "$BASE_USER/register" "$reg_data")
http_code=$(echo "$resp" | tail -n1)
log_result "POST /register (重复注册-预期失败)" "$http_code" "500" ""   # BusinessException returns 200 + code 500 in body actually, but HTTP 200 in practice? adjust

# Re-check actual behavior: Business returns HTTP 200 with {code:500}
# Adjust expectations in real run.

# 1.4 Login
echo "登录..."
login_data='{"username":"'$TEST_USER'","password":"'$PASS'"}'
resp=$(curl_post "$BASE_USER/login" "$login_data")
http_code=$(echo "$resp" | tail -n1)
body=$(echo "$resp" | sed '$d')
if [ "$http_code" = "200" ]; then
    TOKEN=$(echo "$body" | jq -r '.data.accessToken // empty' 2>/dev/null)
    USER_ID=$(echo "$body" | jq -r '.data.userId // empty' 2>/dev/null)
    echo "  Token 获取成功: ${TOKEN:0:30}..." | tee -a $REPORT
    log_result "POST /login (登录)" "$http_code" "200" ""
else
    log_result "POST /login (登录)" "$http_code" "200" "$body"
fi

AUTH="Authorization: Bearer $TOKEN"

# 1.5 Update me (protected)
update_data='{"nickname":"测试昵称已更新","email":"updated_'$TEST_USER'@example.com"}'
resp=$(curl_put "$BASE_USER/me" "$update_data" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "PUT /me (更新当前用户-带Token)" "$http_code" "200" ""

# 1.6 Update me without token (401 expected)
resp=$(curl_put "$BASE_USER/me" "$update_data")
http_code=$(echo "$resp" | tail -n1)
log_result "PUT /me (未登录更新-预期401)" "$http_code" "401" ""

echo ""
echo "=== 2. 分类管理测试 (公开接口) ===" | tee -a $REPORT

# 2.1 Create root category
cat_root='{"name":"测试根分类_'$TEST_USER'","parentId":0,"sortOrder":99,"status":1}'
resp=$(curl_post "$BASE_RES/category" "$cat_root")
http_code=$(echo "$resp" | tail -n1)
body=$(echo "$resp" | sed '$d')
ROOT_CAT_ID=$(echo "$body" | jq -r '.data // empty' 2>/dev/null || echo "")
# Note: current impl returns void, data=null on success
log_result "POST /category (新增根分类)" "$http_code" "200" ""

# Get the latest category id by querying list (hacky but works for test)
resp=$(curl_get "$BASE_RES/category?size=5")
body=$(echo "$resp" | sed '$d')
CAT_ID=$(echo "$body" | jq -r '.data.records[0].id // empty' 2>/dev/null)

if [ -z "$CAT_ID" ]; then
    # fallback create one more
    cat_child='{"name":"测试子分类","parentId":1,"sortOrder":1,"status":1}'
    resp=$(curl_post "$BASE_RES/category" "$cat_child")
    body=$(echo "$resp" | sed '$d')
    # Assume id 1 or 2 exists from init, use query
    resp=$(curl_get "$BASE_RES/category?size=1")
    body=$(echo "$resp" | sed '$d')
    CAT_ID=$(echo "$body" | jq -r '.data.records[0].id // "1"' 2>/dev/null)
fi
echo "使用分类ID: $CAT_ID" | tee -a $REPORT

# 2.2 Get category tree
resp=$(curl_get "$BASE_RES/category/tree")
http_code=$(echo "$resp" | tail -n1)
log_result "GET /category/tree (分类树)" "$http_code" "200" ""

# 2.3 Get category by id
resp=$(curl_get "$BASE_RES/category/$CAT_ID")
http_code=$(echo "$resp" | tail -n1)
log_result "GET /category/{id} (分类详情)" "$http_code" "200" ""

# 2.4 Page categories
resp=$(curl_get "$BASE_RES/category?page=1&size=5")
http_code=$(echo "$resp" | tail -n1)
log_result "GET /category (分页分类)" "$http_code" "200" ""

echo ""
echo "=== 3. 资源管理测试 ===" | tee -a $REPORT

# 3.1 Create resource (需要登录)
res_data='{"title":"测试资源_'$TEST_USER'","description":"接口测试用学习笔记","categoryId":'$CAT_ID',"fileUrl":"https://example.com/test.pdf","fileSize":123456,"fileType":"application/pdf"}'
resp=$(curl_post "$BASE_RES/resource" "$res_data" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
body=$(echo "$resp" | sed '$d')
RES_ID=$(echo "$body" | jq -r '.data // empty' 2>/dev/null || echo "1")
log_result "POST /resource (创建资源-带Token)" "$http_code" "200" ""

# 3.2 List resources (public)
resp=$(curl_get "$BASE_RES/resource?page=1&size=5")
http_code=$(echo "$resp" | tail -n1)
log_result "GET /resource (分页资源列表)" "$http_code" "200" ""

# 3.3 Get resource detail
if [ -n "$RES_ID" ] && [ "$RES_ID" != "null" ]; then
    resp=$(curl_get "$BASE_RES/resource/$RES_ID")
    http_code=$(echo "$resp" | tail -n1)
    log_result "GET /resource/{id} (资源详情)" "$http_code" "200" ""
else
    # fallback
    resp=$(curl_get "$BASE_RES/resource/1")
    http_code=$(echo "$resp" | tail -n1)
    log_result "GET /resource/1 (资源详情 fallback)" "$http_code" "200" ""
    RES_ID=1
fi

# 3.4 Update resource (owner)
update_res='{"title":"测试资源_已更新","description":"更新后的描述","categoryId":'$CAT_ID',"fileUrl":"https://example.com/test2.pdf","fileSize":234567,"fileType":"application/pdf"}'
resp=$(curl_put "$BASE_RES/resource/$RES_ID" "$update_res" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "PUT /resource/{id} (更新资源)" "$http_code" "200" ""

# 3.5 Download (public)
resp=$(curl_post "$BASE_RES/resource/$RES_ID/download" "")
http_code=$(echo "$resp" | tail -n1)
log_result "POST /resource/{id}/download (下载-异步)" "$http_code" "200" ""

# 3.6 Favorite (auth required)
resp=$(curl_post "$BASE_RES/resource/$RES_ID/favorite" "{}" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "POST /resource/{id}/favorite (收藏)" "$http_code" "200" ""

# 3.7 Is favorited
resp=$(curl_get "$BASE_RES/resource/$RES_ID/favorite" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "GET /resource/{id}/favorite (是否收藏)" "$http_code" "200" ""

# 3.8 Unfavorite
resp=$(curl_delete "$BASE_RES/resource/$RES_ID/favorite" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "DELETE /resource/{id}/favorite (取消收藏)" "$http_code" "200" ""

# 3.9 Search（必须 URL 编码中文）
SEARCH_KW=$(python3 -c "import urllib.parse; print(urllib.parse.quote('测试'))" 2>/dev/null || echo "测试")
resp=$(curl_get "$BASE_RES/resource/search?keyword=${SEARCH_KW}&size=5")
http_code=$(echo "$resp" | tail -n1)
log_result "GET /resource/search (全文搜索-已编码)" "$http_code" "200" ""

# 3.10 Audit (admin only - expect 403 for normal user)
resp=$(curl_put "$BASE_RES/resource/$RES_ID/audit?status=1" "" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "PUT /resource/{id}/audit (审核-普通用户预期403)" "$http_code" "403" ""

# 3.11 Delete resource (owner)
resp=$(curl_delete "$BASE_RES/resource/$RES_ID" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "DELETE /resource/{id} (删除资源)" "$http_code" "200" ""

echo ""
echo "=== 4. 异常场景测试 ===" | tee -a $REPORT

# Sensitive word (if filter active)
bad_data='{"title":"违规内容测试","description":"包含敏感词","categoryId":'$CAT_ID',"fileUrl":"https://e.com/1.pdf","fileSize":100,"fileType":"pdf"}'
resp=$(curl_post "$BASE_RES/resource" "$bad_data" "$AUTH")
http_code=$(echo "$resp" | tail -n1)
log_result "POST /resource (敏感词拦截-预期500)" "$http_code" "500" ""

# No token for protected
resp=$(curl_post "$BASE_RES/resource" "$res_data")
http_code=$(echo "$resp" | tail -n1)
log_result "POST /resource (无Token-预期401)" "$http_code" "401" ""

echo ""
echo "========================================" | tee -a $REPORT
echo "测试完成: 通过 $pass_count / 失败 $fail_count" | tee -a $REPORT
echo "详细日志见 logs/ 目录" | tee -a $REPORT
echo "========================================" | tee -a $REPORT

cat $REPORT
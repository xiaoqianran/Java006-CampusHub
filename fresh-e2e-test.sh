#!/bin/bash
# =====================================================
# 时迁平台 - 全新端到端真实用户测试（完全干净数据）
# =====================================================
set -u

BASE_USER="http://localhost:8081/api/user"
BASE_API="http://localhost:8082/api"

# 生成完全唯一的测试数据（用户名必须 ≤ 20 字符）
TS=$(date +%s%N | cut -c1-10)
TEST_USER="e2e${TS}"
TEST_PASS="E2E_Pass_2026!"
TEST_NICK="全流程测试用户_${TS}"
TEST_EMAIL="e2e${TS}@campus.test"
TEST_PHONE="138${TS: -8}"

REPORT="docs/本次全新全量测试报告.md"
mkdir -p docs logs

echo "================================================================" | tee "$REPORT"
echo "时迁校园资源共享平台 - 全新端到端全量接口测试报告" | tee -a "$REPORT"
echo "测试时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "$REPORT"
echo "测试数据前缀: ${TS}" | tee -a "$REPORT"
echo "测试用户: $TEST_USER" | tee -a "$REPORT"
echo "================================================================" | tee -a "$REPORT"

pass=0
fail=0

function record() {
    local name="$1"
    local ok="$2"
    local msg="$3"
    if [ "$ok" = "true" ]; then
        echo "✅ [PASS] $name" | tee -a "$REPORT"
        ((pass++))
    else
        echo "❌ [FAIL] $name - $msg" | tee -a "$REPORT"
        ((fail++))
    fi
}

function api_post() {
    local url="$1"
    local data="$2"
    local auth="${3:-}"
    curl -s -w "\n__HTTP__:%{http_code}" -X POST "$url" \
        -H "Content-Type: application/json" \
        ${auth:+ -H "$auth"} \
        -d "$data" 2>/dev/null
}

function api_get() {
    local url="$1"
    local auth="${2:-}"
    curl -s -w "\n__HTTP__:%{http_code}" -X GET "$url" \
        ${auth:+ -H "$auth"} 2>/dev/null
}

function api_put() {
    local url="$1"
    local data="$2"
    local auth="${3:-}"
    curl -s -w "\n__HTTP__:%{http_code}" -X PUT "$url" \
        -H "Content-Type: application/json" \
        ${auth:+ -H "$auth"} \
        -d "$data" 2>/dev/null
}

function api_delete() {
    local url="$1"
    local auth="${2:-}"
    curl -s -w "\n__HTTP__:%{http_code}" -X DELETE "$url" \
        ${auth:+ -H "$auth"} 2>/dev/null
}

# ==================== 开始真实用户流程 ====================

echo ""
echo ">>> 步骤1: 用户注册与登录" | tee -a "$REPORT"

# 1. 注册
reg_data='{"username":"'$TEST_USER'","password":"'$TEST_PASS'","nickname":"'$TEST_NICK'","email":"'$TEST_EMAIL'","phone":"'$TEST_PHONE'"}'
resp=$(api_post "$BASE_USER/register" "$reg_data")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "用户注册" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 2. 登录
login_data='{"username":"'$TEST_USER'","password":"'$TEST_PASS'"}'
resp=$(api_post "$BASE_USER/login" "$login_data")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
body=$(echo "$resp" | sed '/__HTTP__/d')
TOKEN=$(echo "$body" | jq -r '.data.accessToken // empty' 2>/dev/null)
USER_ID=$(echo "$body" | jq -r '.data.userId // empty' 2>/dev/null)

if [ -n "$TOKEN" ] && [ "$http" = "200" ]; then
    record "用户登录获取Token" true "userId=$USER_ID"
else
    record "用户登录获取Token" false "HTTP $http"
    exit 1
fi

AUTH="Authorization: Bearer $TOKEN"

# 3. 更新资料
update='{"nickname":"全流程测试用户_已更新","avatar":"https://cdn.test/avatar/'$TS'.png"}'
resp=$(api_put "$BASE_USER/me" "$update" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "更新当前用户信息" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

echo ""
echo ">>> 步骤2: 分类管理（真实创建分类树）" | tee -a "$REPORT"

# 创建一级分类
cat1='{"name":"全流程测试大类_'$TS'","parentId":0,"sortOrder":100,"status":1}'
resp=$(api_post "$BASE_API/category" "$cat1")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "新增一级分类" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 获取最新分类ID
resp=$(api_get "$BASE_API/category?size=3")
CAT_ID=$(echo "$resp" | sed '/__HTTP__/d' | jq -r '.data.records[0].id // "1"' 2>/dev/null)

# 创建子分类
cat2='{"name":"全流程测试子类_'$TS'","parentId":'$CAT_ID',"sortOrder":1,"status":1}'
resp=$(api_post "$BASE_API/category" "$cat2")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "新增子分类" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 查询分类树
resp=$(api_get "$BASE_API/category/tree")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "获取分类树" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

echo ""
echo ">>> 步骤3: 资源全生命周期（核心业务）" | tee -a "$REPORT"

# 创建资源
res_data='{"title":"全流程测试资源_'$TS'","description":"这是真实用户从注册到删除的完整流程测试资源","categoryId":'$CAT_ID',"fileUrl":"https://oss.test/res/'$TS'.pdf","fileSize":1234567,"fileType":"application/pdf"}'
resp=$(api_post "$BASE_API/resource" "$res_data" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
body=$(echo "$resp" | sed '/__HTTP__/d')
record "创建资源" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 获取刚创建的资源ID
resp=$(api_get "$BASE_API/resource?size=1" "$AUTH")
RES_ID=$(echo "$resp" | sed '/__HTTP__/d' | jq -r '.data.records[0].id // "1"' 2>/dev/null)

# 列表
resp=$(api_get "$BASE_API/resource?page=1&size=5")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "分页查询资源列表" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 详情
resp=$(api_get "$BASE_API/resource/$RES_ID")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "获取资源详情" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 更新资源
update_res='{"title":"全流程测试资源_已更新_'$TS'","description":"更新后的描述内容","categoryId":'$CAT_ID',"fileUrl":"https://oss.test/res/'$TS'-v2.pdf","fileSize":2345678,"fileType":"application/pdf"}'
resp=$(api_put "$BASE_API/resource/$RES_ID" "$update_res" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "更新资源" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

echo ""
echo ">>> 步骤4: 下载 + 收藏 + 搜索（验证修复后的功能）" | tee -a "$REPORT"

# 下载（关键验证 RabbitMQ 修复）
resp=$(api_post "$BASE_API/resource/$RES_ID/download" "" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "下载资源（MQ异步统计）" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 收藏
resp=$(api_post "$BASE_API/resource/$RES_ID/favorite" "" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "收藏资源" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 查询是否收藏
resp=$(api_get "$BASE_API/resource/$RES_ID/favorite" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
body=$(echo "$resp" | sed '/__HTTP__/d')
is_fav=$(echo "$body" | jq -r '.data // false' 2>/dev/null)
record "查询是否已收藏" "$([ "$http" = "200" ] && [ "$is_fav" = "true" ] && echo true || echo false)" "HTTP $http, isFavorited=$is_fav"

# 取消收藏
resp=$(api_delete "$BASE_API/resource/$RES_ID/favorite" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "取消收藏" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 全文搜索（中文 + 编码）
SEARCH_KW=$(python3 -c "import urllib.parse, sys; print(urllib.parse.quote('全流程'))" 2>/dev/null || echo "全流程")
resp=$(api_get "$BASE_API/resource/search?keyword=${SEARCH_KW}&size=5")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "全文搜索（中文已编码）" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

echo ""
echo ">>> 步骤5: 权限验证 + 删除" | tee -a "$REPORT"

# 普通用户尝试审核（必须 403）
resp=$(api_put "$BASE_API/resource/$RES_ID/audit?status=1" "" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "普通用户审核资源（预期403）" "$([ "$http" = "403" ] && echo true || echo false)" "HTTP $http"

# 删除资源
resp=$(api_delete "$BASE_API/resource/$RES_ID" "$AUTH")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "删除资源" "$([ "$http" = "200" ] && echo true || echo false)" "HTTP $http"

# 再次获取已被删除的资源（预期404或业务错误）
resp=$(api_get "$BASE_API/resource/$RES_ID")
http=$(echo "$resp" | grep __HTTP__ | cut -d: -f2)
record "获取已删除资源（预期非200）" "$([ "$http" != "200" ] && echo true || echo false)" "HTTP $http"

echo ""
echo "================================================================" | tee -a "$REPORT"
echo "测试完成" | tee -a "$REPORT"
echo "通过: $pass   失败: $fail" | tee -a "$REPORT"
echo "================================================================" | tee -a "$REPORT"

if [ $fail -eq 0 ]; then
    echo "🎉 所有接口测试全部通过！后端服务状态良好。" | tee -a "$REPORT"
else
    echo "⚠️ 存在 $fail 个问题，请查看上方详情。" | tee -a "$REPORT"
fi

cat "$REPORT"
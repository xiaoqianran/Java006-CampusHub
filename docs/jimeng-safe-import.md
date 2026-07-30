# 即梦内容安全导入

导入器把旧库 `jimeng_prompts` 映射到 CampusHub 的“图片”频道：

- `work_id` → 外部幂等标识，相同内容重复执行只更新、不重复新增。
- `prompt` → **正文提示词只写一次**；标题截断展示，摘要只放作者/模型等元信息。
- `author`、`model`、`aspect_ratio` → 自由标签。
- `image_high` 优先、`image_url` 兜底 → 下载到本地附件存储并设为封面。
- 新内容以管理员身份直接发布；已有内容的审核状态、删除状态、统计数据不会被重置。

## 数量对不上时先看这条链

油猴面板「已收集 N」是**浏览器本地**数量，不等于 CampusHub / 中间 MySQL 数量：

1. 油猴本地 `GM_setValue`（例如 23759）
2. 同步 API → 中间库 `jimeng_prompts`（可能只有几千）
3. CampusHub 导入器 / 直连同步 → `t_resource`（图片频道）

签名图链（`x-expires`）过期后无法再下载，只能重新采集或等油猴重新抓到新 URL。

## 油猴直连 CampusHub（推荐）

CampusHub 已提供与脚本兼容的接口（仅本机可写）：

- `POST /api/jimeng/prompts/batch`
- `POST /api/jimeng/prompts/stream`
- `POST /api/jimeng/prompts/existing`

默认油猴写的是 `http://127.0.0.1:3001/...`。可启动本仓库代理：

```bash
export JIMENG_INGEST_TOKEN="<至少 32 位随机令牌>"
python3 scripts/jimeng-sync-proxy.py http://127.0.0.1:8080 3001
```

然后在即梦网页油猴面板点「同步 MySQL / 校准」。新数据会直接进入 CampusHub 图片频道并尽量落盘图片。
资源服务必须使用相同的 `JIMENG_INGEST_TOKEN`。接口同时校验共享令牌和本机来源；
未配置令牌时默认关闭写入。
本机代理只转发三个即梦同步端点，并默认只接受 `https://jimeng.jianying.com` 来源。

## 安全约束

- 源库事务强制为只读，只执行分页 `SELECT`。
- 密码只从环境变量读取，不写入代码、脚本参数或 Git。
- 仓库不保存真实数据库 IP、用户名、密码或导出的 JSONL 数据。
- 默认每次最多读取 100 条，显式 `--limit` 最大为 20000。
- 图片只允许 HTTPS 和指定的即梦 CDN 域名，并拒绝私网解析。
- 下载默认 4 并发，最大允许 8；单图默认不超过 20 MiB。
- 图片通过文件头校验后才会原子写入正式路径。
- 已过期或下载失败的图片不会阻断提示词入库；源链接刷新后可幂等重跑补图。

## 使用

先通过环境变量提供连接信息，不要把真实密码写进仓库：

```bash
export JIMENG_DB_HOST="<旧库地址>"
export JIMENG_DB_PORT="3306"
export JIMENG_DB_NAME="jimeng"
export JIMENG_DB_USER="<只读账号>"
export JIMENG_DB_PASSWORD="<旧库密码>"

export CAMPUSHUB_DB_HOST="127.0.0.1"
export CAMPUSHUB_DB_PORT="3306"
export CAMPUSHUB_DB_NAME="shiqian_resource"
export CAMPUSHUB_DB_USER="root"
export CAMPUSHUB_DB_PASSWORD="<本地库密码>"
```

先预演：

```bash
./scripts/import-jimeng-prompts.sh --limit=20 --dry-run
```

小批量正式导入：

```bash
./scripts/import-jimeng-prompts.sh --limit=20
```

全量导入或断点续传：

```bash
./scripts/import-jimeng-prompts.sh --limit=2500
./scripts/import-jimeng-prompts.sh --after-id=1000 --limit=500
```

只导入提示词、不下载图片：

```bash
./scripts/import-jimeng-prompts.sh --limit=2500 --metadata-only
```

已有油猴 JSONL 导出文件时，可直接写入 CampusHub（默认不会回写旧库）：

```bash
python3 scripts/import-jimeng-jsonl.py \
  --file=/path/to/jimeng_gallery.jsonl \
  --download
```

只有明确需要修复旧中间库时才添加 `--sync-source`，并提供
`JIMENG_DB_HOST`、`JIMENG_DB_USER`、`JIMENG_DB_PASSWORD`。

可选控制项：

```bash
export JIMENG_IMPORT_USER_ID="1"
export JIMENG_DOWNLOAD_CONCURRENCY="4"
export JIMENG_MAX_IMAGE_BYTES="20971520"
export JIMENG_MIN_FREE_BYTES="536870912"
export CAMPUSHUB_UPLOAD_ROOT="uploads/resources"
export JIMENG_ALLOWED_IMAGE_HOSTS="p11-dreamina-sign.byteimg.com,p26-dreamina-sign.byteimg.com"
```

# 即梦内容安全导入

导入器把旧库 `jimeng_prompts` 映射到 CampusHub 的“图片”频道：

- `work_id` → 外部幂等标识，相同内容重复执行只更新、不重复新增。
- `prompt` → 标题、摘要和 Markdown 正文。
- `author`、`model`、`aspect_ratio` → 自由标签。
- `image_high` 优先、`image_url` 兜底 → 下载到本地附件存储并设为封面。
- 新内容以管理员身份直接发布；已有内容的审核状态、删除状态、统计数据不会被重置。

## 安全约束

- 源库事务强制为只读，只执行分页 `SELECT`。
- 密码只从环境变量读取，不写入代码、脚本参数或 Git。
- 默认每次最多读取 100 条，显式 `--limit` 最大为 5000。
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

可选控制项：

```bash
export JIMENG_IMPORT_USER_ID="1"
export JIMENG_DOWNLOAD_CONCURRENCY="4"
export JIMENG_MAX_IMAGE_BYTES="20971520"
export JIMENG_MIN_FREE_BYTES="536870912"
export CAMPUSHUB_UPLOAD_ROOT="uploads/resources"
export JIMENG_ALLOWED_IMAGE_HOSTS="p11-dreamina-sign.byteimg.com,p26-dreamina-sign.byteimg.com"
```

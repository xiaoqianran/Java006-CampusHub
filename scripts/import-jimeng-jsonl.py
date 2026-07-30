#!/usr/bin/env python3
"""将油猴导出的 jsonl 幂等写入：
1) 远程 jimeng.jimeng_prompts
2) 本地 shiqian_resource.t_resource（图片频道）
3) 可选：下载仍有效的图片到 uploads/resources
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

try:
    import pymysql
except ImportError:
    print(
        "缺少依赖 pymysql，请先执行：python3 -m pip install pymysql",
        file=sys.stderr,
    )
    raise SystemExit(2)


def normalize_ws(value: str | None) -> str:
    if not value:
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def truncate_cp(value: str, max_cp: int) -> str:
    if not value:
        return ""
    chars = list(value)
    if len(chars) <= max_cp:
        return value
    return "".join(chars[: max(1, max_cp - 1)]) + "…"


def display_title(prompt: str) -> str:
    text = normalize_ws(prompt)
    return truncate_cp(text, 42) if text else "即梦作品"


def display_summary(author: str | None, model: str | None, aspect: str | None) -> str:
    parts = []
    for item in (author, model, aspect):
        text = normalize_ws(item)
        if text:
            parts.append(text)
    if not parts:
        return "即梦 AI 作品"
    return truncate_cp("即梦 · " + " · ".join(parts), 120)


def build_tags(author: str | None, model: str | None, aspect: str | None) -> str:
    tags = ["即梦"]
    for item in (author, model, aspect):
        text = normalize_ws(item)
        if text:
            tags.append(truncate_cp(text.replace(",", "，"), 80))
    return truncate_cp(", ".join(tags), 500)


def parse_collected_at(value: Any) -> datetime | None:
    if not value:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        return datetime.fromisoformat(text).astimezone(timezone.utc).replace(tzinfo=None)
    except Exception:
        try:
            return datetime.strptime(text[:19], "%Y-%m-%d %H:%M:%S")
        except Exception:
            return None


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def detect_ext(header: bytes) -> tuple[str, str] | None:
    if len(header) >= 12 and header[0:4] == b"RIFF" and header[8:12] == b"WEBP":
        return "webp", "image/webp"
    if len(header) >= 3 and header[0:3] == b"\xff\xd8\xff":
        return "jpg", "image/jpeg"
    if len(header) >= 8 and header[0:8] == b"\x89PNG\r\n\x1a\n":
        return "png", "image/png"
    if len(header) >= 6 and header[0:6] in (b"GIF87a", b"GIF89a"):
        return "gif", "image/gif"
    return None


def url_not_expired(url: str, now: float) -> bool:
    if not url:
        return False
    exp = parse_qs(urlparse(url).query).get("x-expires", [None])[0]
    if not exp:
        return True
    try:
        return int(exp) > now
    except Exception:
        return False


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    bad = 0
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except Exception:
                bad += 1
                continue
            work_id = str(item.get("work_id") or item.get("workId") or "").strip()
            prompt = str(item.get("prompt") or "").strip()
            if not work_id or not prompt or work_id in seen:
                bad += 1
                continue
            seen.add(work_id)
            aspect = item.get("aspect_ratio")
            if aspect is not None and not isinstance(aspect, str):
                aspect = str(aspect)
            rows.append(
                {
                    "work_id": work_id,
                    "prompt": prompt,
                    "author": item.get("author") or None,
                    "model": item.get("model") or None,
                    "create_time": item.get("create_time"),
                    "collected_at": item.get("collected_at"),
                    "image_url": item.get("image_url") or item.get("imageUrl") or None,
                    "image_high": item.get("image_high") or item.get("imageHigh") or None,
                    "aspect_ratio": aspect,
                    "raw": item,
                }
            )
    print(f"[jsonl] ok={len(rows)} skipped={bad} file={path}")
    return rows


def connect_mysql(host: str, port: int, user: str, password: str, database: str):
    return pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        database=database,
        charset="utf8mb4",
        autocommit=False,
        connect_timeout=20,
        read_timeout=300,
        write_timeout=300,
    )


def upsert_remote(conn, rows: list[dict[str, Any]], batch_size: int = 50) -> int:
    # 不写 raw_json，避免大 JSON CAST 卡住远程库
    sql = """
    INSERT INTO jimeng_prompts (
      work_id, prompt, author, model, create_time, collected_at,
      image_url, image_high, aspect_ratio
    ) VALUES (
      %s, %s, %s, %s, %s, %s,
      %s, %s, %s
    )
    ON DUPLICATE KEY UPDATE
      prompt = VALUES(prompt),
      author = VALUES(author),
      model = VALUES(model),
      create_time = VALUES(create_time),
      collected_at = VALUES(collected_at),
      image_url = VALUES(image_url),
      image_high = VALUES(image_high),
      aspect_ratio = VALUES(aspect_ratio),
      updated_at = CURRENT_TIMESTAMP
    """
    total = 0
    with conn.cursor() as cur:
        for i in range(0, len(rows), batch_size):
            chunk = rows[i : i + batch_size]
            params = []
            for row in chunk:
                collected = parse_collected_at(row.get("collected_at"))
                create_time = row.get("create_time")
                if create_time is not None:
                    try:
                        create_time = int(create_time)
                    except Exception:
                        create_time = None
                params.append(
                    (
                        row["work_id"],
                        row["prompt"],
                        row.get("author"),
                        row.get("model"),
                        create_time,
                        collected,
                        row.get("image_url"),
                        row.get("image_high"),
                        row.get("aspect_ratio"),
                    )
                )
            try:
                cur.executemany(sql, params)
                conn.commit()
            except Exception as error:
                conn.rollback()
                # 降到单条重试，跳过坏行
                print(f"[remote] batch failed at {total}: {error}; fallback single", flush=True)
                for param in params:
                    try:
                        cur.execute(sql, param)
                        conn.commit()
                        total += 1
                    except Exception as one_err:
                        conn.rollback()
                        print(f"[remote] skip {param[0]}: {one_err}", flush=True)
                print(f"[remote] upserted {total}/{len(rows)}", flush=True)
                continue
            total += len(chunk)
            print(f"[remote] upserted {total}/{len(rows)}", flush=True)
    return total


def find_existing_local_images(upload_root: Path, user_id: int, work_id: str) -> tuple[str, str, int, str, str] | None:
    stem = "jimeng-" + sha256_hex(work_id)[:32]
    user_dir = upload_root / str(user_id)
    for ext, mime in (("webp", "image/webp"), ("jpg", "image/jpeg"), ("png", "image/png"), ("gif", "image/gif")):
        path = user_dir / f"{stem}.{ext}"
        if path.is_file() and path.stat().st_size > 0:
            name = path.name
            return name, f"/api/resource/files/{user_id}/{name}", path.stat().st_size, ext, mime
    return None


def download_image(
    work_id: str,
    image_high: str | None,
    image_url: str | None,
    upload_root: Path,
    user_id: int,
    allowed_hosts: set[str],
    max_bytes: int,
) -> tuple[str, str, int, str, str] | None:
    existing = find_existing_local_images(upload_root, user_id, work_id)
    if existing:
        return existing

    candidates = []
    for url in (image_high, image_url):
        if url and url not in candidates:
            candidates.append(url)
    now = time.time()
    user_dir = upload_root / str(user_id)
    user_dir.mkdir(parents=True, exist_ok=True)
    stem = "jimeng-" + sha256_hex(work_id)[:32]

    for url in candidates:
        if not url_not_expired(url, now):
            continue
        try:
            parsed = urlparse(url)
            if parsed.scheme != "https" or not parsed.hostname:
                continue
            host = parsed.hostname.lower()
            if host not in allowed_hosts:
                continue
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "Mozilla/5.0 CampusHub-Jimeng-Jsonl/1.0",
                    "Referer": "https://jimeng.jianying.com/",
                    "Accept": "image/avif,image/webp,image/png,image/jpeg,image/gif",
                },
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=25) as resp:
                data = resp.read(max_bytes + 1)
            if len(data) == 0 or len(data) > max_bytes:
                continue
            detected = detect_ext(data[:12])
            if not detected:
                continue
            ext, mime = detected
            target = user_dir / f"{stem}.{ext}"
            tmp = user_dir / f"{stem}.{ext}.part"
            tmp.write_bytes(data)
            tmp.replace(target)
            name = target.name
            return name, f"/api/resource/files/{user_id}/{name}", target.stat().st_size, ext, mime
        except Exception:
            continue
    return None


def upsert_local(
    conn,
    rows: list[dict[str, Any]],
    upload_root: Path,
    user_id: int,
    download: bool,
    workers: int,
    allowed_hosts: set[str],
) -> dict[str, int]:
    stats = {"upserted": 0, "images": 0, "no_image": 0}

    image_map: dict[str, tuple[str, str, int, str, str] | None] = {}
    for row in rows:
        image_map[row["work_id"]] = find_existing_local_images(upload_root, user_id, row["work_id"])

    upsert_sql = """
      INSERT INTO t_resource (
        user_id, title, description, summary, content_markdown,
        content_type, content_scene, tags, external_source, external_id,
        category_id, file_url, file_size, file_type,
        download_count, view_count, version, status,
        review_reason, reviewer_id, review_time, published_time,
        create_time, update_time, deleted
      ) VALUES (
        %s, %s, %s, %s, %s,
        %s, 'GALLERY', %s, 'JIMENG', %s,
        NULL, %s, %s, %s,
        0, 0, 1, 1,
        %s, %s, UTC_TIMESTAMP(), %s,
        %s, UTC_TIMESTAMP(), 0
      )
      ON DUPLICATE KEY UPDATE
        title = VALUES(title),
        description = VALUES(description),
        summary = VALUES(summary),
        content_markdown = VALUES(content_markdown),
        content_type = IF(VALUES(file_url) IS NOT NULL, 'MIXED', content_type),
        content_scene = 'GALLERY',
        tags = VALUES(tags),
        file_url = COALESCE(VALUES(file_url), file_url),
        file_size = IF(VALUES(file_url) IS NULL, file_size, VALUES(file_size)),
        file_type = COALESCE(VALUES(file_type), file_type),
        update_time = UTC_TIMESTAMP()
    """
    find_id_sql = """
      SELECT id FROM t_resource
      WHERE external_source='JIMENG' AND external_id=%s
      ORDER BY deleted ASC, id DESC LIMIT 1
    """
    find_cover_sql = """
      SELECT id FROM t_resource_attachment
      WHERE resource_id=%s AND usage_type='COVER'
      ORDER BY id LIMIT 1
    """
    insert_cover_sql = """
      INSERT INTO t_resource_attachment (
        resource_id, file_name, file_url, file_size, file_type,
        mime_type, asset_kind, usage_type, sort_order, create_time
      ) VALUES (%s,%s,%s,%s,%s,%s,'IMAGE','COVER',0,UTC_TIMESTAMP())
    """
    update_cover_sql = """
      UPDATE t_resource_attachment
      SET file_name=%s, file_url=%s, file_size=%s, file_type=%s,
          mime_type=%s, asset_kind='IMAGE', sort_order=0
      WHERE id=%s
    """

    def remote_image_tuple(row: dict[str, Any]) -> tuple[str, str, int, str, str] | None:
        """默认不下载：优先 image_high，其次 image_url，直接当封面直链。"""
        for key in ("image_high", "image_url"):
            url = (row.get(key) or "").strip()
            parsed = urlparse(url)
            if (
                parsed.scheme == "https"
                and parsed.hostname
                and parsed.hostname.lower() in allowed_hosts
                and url_not_expired(url, time.time())
            ):
                name = f"jimeng-{row['work_id']}.webp"
                return name, url, 0, "webp", "image/webp"
        return None

    # 第一阶段：元数据写入。默认用 CDN 直链；仅 --download 时才落盘。
    with conn.cursor() as cur:
        for idx, row in enumerate(rows, 1):
            image = image_map.get(row["work_id"])
            if image is None and not download:
                image = remote_image_tuple(row)
            title = display_title(row["prompt"])
            summary = display_summary(row.get("author"), row.get("model"), row.get("aspect_ratio"))
            tags = build_tags(row.get("author"), row.get("model"), row.get("aspect_ratio"))
            content = normalize_ws(row["prompt"])
            content_type = "MIXED" if image else "ARTICLE"
            file_url = image[1] if image else None
            file_size = image[2] if image else 0
            file_type = image[3] if image else None
            source_time = None
            ct = row.get("create_time")
            try:
                if ct is not None and 946684800 < int(ct) < 4102444800:
                    source_time = datetime.utcfromtimestamp(int(ct))
            except Exception:
                source_time = None
            if source_time is None:
                source_time = parse_collected_at(row.get("collected_at")) or datetime.utcnow()

            cur.execute(
                upsert_sql,
                (
                    user_id,
                    title,
                    summary,
                    summary,
                    content,
                    content_type,
                    tags,
                    row["work_id"],
                    file_url,
                    file_size,
                    file_type,
                    "即梦 JSONL 导入",
                    user_id,
                    source_time,
                    source_time,
                ),
            )
            if image:
                cur.execute(find_id_sql, (row["work_id"],))
                found = cur.fetchone()
                if found:
                    resource_id = found[0]
                    file_name, file_url, file_size, file_type, mime = image
                    cur.execute(find_cover_sql, (resource_id,))
                    cover = cur.fetchone()
                    if cover:
                        cur.execute(
                            update_cover_sql,
                            (file_name, file_url, file_size, file_type, mime, cover[0]),
                        )
                    else:
                        cur.execute(
                            insert_cover_sql,
                            (resource_id, file_name, file_url, file_size, file_type, mime),
                        )
                stats["images"] += 1
            else:
                stats["no_image"] += 1
            stats["upserted"] = idx
            if idx % 500 == 0 or idx == len(rows):
                conn.commit()
                print(
                    f"[local:meta] {idx}/{len(rows)} with_image_url={stats['images']}",
                    flush=True,
                )
    conn.commit()
    print(f"[local] metadata done {stats}", flush=True)

    if not download:
        return stats

    now = time.time()
    need = [
        row
        for row in rows
        if image_map.get(row["work_id"]) is None
        and (
            url_not_expired(row.get("image_high") or "", now)
            or url_not_expired(row.get("image_url") or "", now)
        )
    ]
    print(f"[local] downloading remaining valid urls: {len(need)}", flush=True)
    if not need:
        return stats

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(
                download_image,
                row["work_id"],
                row.get("image_high"),
                row.get("image_url"),
                upload_root,
                user_id,
                allowed_hosts,
                20 * 1024 * 1024,
            ): row
            for row in need
        }
        done = 0
        with conn.cursor() as cur:
            for fut in as_completed(futures):
                row = futures[fut]
                done += 1
                try:
                    image = fut.result()
                except Exception:
                    image = None
                if not image:
                    if done % 50 == 0 or done == len(futures):
                        print(f"[local:dl] {done}/{len(futures)}", flush=True)
                    continue
                image_map[row["work_id"]] = image
                title = display_title(row["prompt"])
                summary = display_summary(row.get("author"), row.get("model"), row.get("aspect_ratio"))
                tags = build_tags(row.get("author"), row.get("model"), row.get("aspect_ratio"))
                content = normalize_ws(row["prompt"])
                file_name, file_url, file_size, file_type, mime = image
                source_time = parse_collected_at(row.get("collected_at")) or datetime.utcnow()
                cur.execute(
                    upsert_sql,
                    (
                        user_id,
                        title,
                        summary,
                        summary,
                        content,
                        "MIXED",
                        tags,
                        row["work_id"],
                        file_url,
                        file_size,
                        file_type,
                        "即梦 JSONL 导入",
                        user_id,
                        source_time,
                        source_time,
                    ),
                )
                cur.execute(find_id_sql, (row["work_id"],))
                found = cur.fetchone()
                if found:
                    resource_id = found[0]
                    cur.execute(find_cover_sql, (resource_id,))
                    cover = cur.fetchone()
                    if cover:
                        cur.execute(
                            update_cover_sql,
                            (file_name, file_url, file_size, file_type, mime, cover[0]),
                        )
                    else:
                        cur.execute(
                            insert_cover_sql,
                            (resource_id, file_name, file_url, file_size, file_type, mime),
                        )
                    stats["images"] += 1
                    stats["no_image"] = max(0, stats["no_image"] - 1)
                if done % 50 == 0 or done == len(futures):
                    conn.commit()
                    print(f"[local:dl] {done}/{len(futures)} images={stats['images']}", flush=True)
        conn.commit()

    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description="Import Jimeng jsonl to CampusHub MySQL")
    parser.add_argument("--file", default="jimeng_gallery_2026-07-30.jsonl")
    parser.add_argument("--skip-local", action="store_true")
    parser.add_argument(
        "--sync-source",
        action="store_true",
        help="显式允许把 JSONL 回写旧 jimeng_prompts；默认绝不写源库",
    )
    parser.add_argument(
        "--download",
        action="store_true",
        help="下载图片到本地（默认不下载，直接用 image_high/image_url 直链展示）",
    )
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--user-id", type=int, default=1)
    parser.add_argument("--upload-root", default="uploads/resources")
    parser.add_argument("--remote-batch", type=int, default=50)
    args = parser.parse_args()
    if not 1 <= args.workers <= 8:
        parser.error("--workers 必须在 1 到 8 之间")
    if not 1 <= args.remote_batch <= 500:
        parser.error("--remote-batch 必须在 1 到 500 之间")
    if args.skip_local and not args.sync_source:
        parser.error("--skip-local 与未启用 --sync-source 同时使用时没有任何工作可做")

    # 实时日志
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except Exception:
        pass

    root = Path(__file__).resolve().parents[1]
    jsonl_path = Path(args.file)
    if not jsonl_path.is_absolute():
        jsonl_path = root / jsonl_path
    if not jsonl_path.is_file():
        print(f"文件不存在: {jsonl_path}", file=sys.stderr)
        return 2

    rows = load_jsonl(jsonl_path)
    if not rows:
        print("没有可导入记录")
        return 1

    remote_host = os.environ.get("JIMENG_DB_HOST", "")
    remote_port = int(os.environ.get("JIMENG_DB_PORT", "3306"))
    remote_db = os.environ.get("JIMENG_DB_NAME", "jimeng")
    remote_user = os.environ.get("JIMENG_DB_USER", "")
    remote_password = os.environ.get("JIMENG_DB_PASSWORD", "")

    local_host = os.environ.get("CAMPUSHUB_DB_HOST", "127.0.0.1")
    local_port = int(os.environ.get("CAMPUSHUB_DB_PORT", "3306"))
    local_db = os.environ.get("CAMPUSHUB_DB_NAME", "shiqian_resource")
    local_user = os.environ.get("CAMPUSHUB_DB_USER", "root")
    local_password = os.environ.get("CAMPUSHUB_DB_PASSWORD", "")

    if not args.skip_local and not local_password:
        parser.error("缺少 CAMPUSHUB_DB_PASSWORD")
    if args.sync_source:
        missing = [
            name
            for name, value in (
                ("JIMENG_DB_HOST", remote_host),
                ("JIMENG_DB_USER", remote_user),
                ("JIMENG_DB_PASSWORD", remote_password),
            )
            if not value
        ]
        if missing:
            parser.error("启用 --sync-source 时缺少环境变量：" + ", ".join(missing))

    allowed = {
        h.strip().lower()
        for h in os.environ.get(
            "JIMENG_ALLOWED_IMAGE_HOSTS",
            "p11-dreamina-sign.byteimg.com,p26-dreamina-sign.byteimg.com",
        ).split(",")
        if h.strip()
    }

    errors: list[str] = []

    # 本地优先：先把元数据+可下图片灌进 CampusHub，远程并行
    def run_local() -> None:
        if args.skip_local:
            return
        print(f"[local] connecting {local_host}:{local_port}/{local_db}", flush=True)
        local = connect_mysql(local_host, local_port, local_user, local_password, local_db)
        try:
            stats = upsert_local(
                local,
                rows,
                upload_root=(root / args.upload_root).resolve(),
                user_id=args.user_id,
                download=args.download,
                workers=max(1, args.workers),
                allowed_hosts=allowed,
            )
            print("[local] done", stats, flush=True)
        except Exception as error:
            errors.append(f"local: {error}")
            print(f"[local] FAILED {error}", flush=True)
        finally:
            local.close()

    def run_remote() -> None:
        if not args.sync_source:
            return
        print(f"[remote] connecting {remote_host}:{remote_port}/{remote_db}", flush=True)
        remote = connect_mysql(remote_host, remote_port, remote_user, remote_password, remote_db)
        try:
            count = upsert_remote(remote, rows, batch_size=max(1, args.remote_batch))
            print(f"[remote] done count={count}", flush=True)
        except Exception as error:
            errors.append(f"remote: {error}")
            print(f"[remote] FAILED {error}", flush=True)
        finally:
            remote.close()

    # 本地与远程并行，缩短总时间
    from concurrent.futures import ThreadPoolExecutor

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(run_local), pool.submit(run_remote)]
        for fut in as_completed(futures):
            fut.result()

    if errors:
        print("完成但有错误:", errors, flush=True)
        return 1
    print("全部完成", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""压缩 app/src/main/assets 下的 webp 资源，减小 APK 体积。

背景
    anim/ 是 512x512 的序列帧动画、img/ 是 720x720 静图，合计约 93MB。
    手机上这些图实际显示尺寸远小于原始分辨率，等比缩小不会有肉眼可见的损失，
    但体积能砍掉一半以上，直接改善弱网下的下载成功率。

用法
    python shrink_assets.py --dry-run          # 只估算，不动任何文件
    python shrink_assets.py                    # 就地压缩（自动备份）
    python shrink_assets.py --anim-size 224 --anim-q 68 --img-size 320
    python shrink_assets.py --restore          # 从备份还原
    python shrink_assets.py --verify           # 检查压缩结果

默认参数
    anim: 512 -> 256px, quality 72    img: 720 -> 360px, quality 82
    这些值在 Pixel 等比屏幕 2x 缩放下肉眼几乎无差别；想更狠就往下调。

注意
    - 已经是目标尺寸的文件会跳过（可重复执行，不会二次劣化）。
    - 备份放在 assets/.shrink-backup/ 下，保留原始路径结构，--restore 可原样还原。
    - 依赖 Pillow：pip install pillow
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
import time
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("缺少 Pillow，请先执行：pip install pillow")

HERE = Path(__file__).resolve().parent


def _find_root(start: Path) -> Path:
    """向上找项目根：以「含 app/src/main/assets 的那一层」为准，脚本放哪都能跑。"""
    for p in (start, *start.parents):
        if (p / "app" / "src" / "main" / "assets").is_dir():
            return p
    return start


ROOT = _find_root(HERE)
ASSETS = ROOT / "app" / "src" / "main" / "assets"
ANIM_DIR = ASSETS / "anim"
IMG_DIR = ASSETS / "img"
BACKUP_DIR = ASSETS / ".shrink-backup"

# 默认压缩参数，可通过命令行覆盖
DEFAULTS = {
    "anim_size": 256,
    "anim_q": 72,
    "img_size": 360,
    "img_q": 82,
}

# webp 的 alpha 通道单独量化，压低它对带透明背景的图有奇效
ALPHA_Q = 80


def _human(n: float) -> str:
    """把字节数格式化成人类可读的形式。"""
    for unit in ("B", "KB", "MB", "GB"):
        if abs(n) < 1024 or unit == "GB":
            return f"{n:.1f}{unit}" if unit != "B" else f"{int(n)}B"
        n /= 1024
    return f"{n:.1f}GB"


def _scan(directory: Path) -> list[Path]:
    """列出目录下所有 webp 文件（不递归，资源是平铺的）。"""
    if not directory.is_dir():
        return []
    return sorted(p for p in directory.iterdir() if p.suffix.lower() == ".webp")


def _compress_one(job: tuple[str, str, int, int, bool]) -> tuple[str, int, int, str]:
    """压缩单个 webp。

    kwargs:
        job: (源路径, 目标路径, 目标边长, 质量, 是否为动图)

    Returns:
        (文件名, 原字节, 新字节, 状态说明)
    """
    src_s, dst_s, size, quality, is_anim = job
    src, dst = Path(src_s), Path(dst_s)
    original = src.stat().st_size

    try:
        im = Image.open(src)
        # 已经是目标尺寸或更小，跳过（保证可重复执行不劣化）
        if max(im.size) <= size:
            im.close()
            return (src.name, original, original, "skip(已达标)")

        frames: list[Image.Image] = []
        durations: list[int] = []
        n = getattr(im, "n_frames", 1) if is_anim else 1
        for i in range(n):
            im.seek(i)
            frames.append(im.convert("RGBA").resize((size, size), Image.LANCZOS))
            durations.append(int(im.info.get("duration") or 100))
        im.close()

        tmp = dst.with_suffix(".tmp")
        if is_anim and len(frames) > 1:
            frames[0].save(
                tmp,
                "WEBP",
                save_all=True,
                append_images=frames[1:],
                duration=durations,
                loop=0,
                quality=quality,
                alpha_quality=ALPHA_Q,
                method=6,
            )
        else:
            frames[0].save(
                tmp, "WEBP", quality=quality, alpha_quality=ALPHA_Q, method=6
            )

        new_size = tmp.stat().st_size
        # 万一压完反而更大（极少数高噪点图），保留原文件
        if new_size >= original:
            tmp.unlink(missing_ok=True)
            return (src.name, original, original, "skip(压缩无收益)")

        os.replace(tmp, dst)
        return (src.name, original, new_size, "ok")
    except Exception as exc:  # 单张失败不影响整体
        return (src.name, original, original, f"fail({type(exc).__name__})")


def _worker_count() -> int:
    """压缩是 CPU 密集型，按核心数并发，但别把机器跑满。"""
    return max(1, min(os.cpu_count() or 4, 16))


def run(mode: str, args: argparse.Namespace) -> int:
    """执行压缩/估算/还原/校验。"""
    if mode == "restore":
        if not BACKUP_DIR.is_dir():
            print(f"没有找到备份目录：{BACKUP_DIR}")
            return 1
        restored = 0
        for sub in ("anim", "img"):
            src_dir = BACKUP_DIR / sub
            if not src_dir.is_dir():
                continue
            for f in src_dir.iterdir():
                if f.suffix.lower() != ".webp":
                    continue
                shutil.copy2(f, ASSETS / sub / f.name)
                restored += 1
        print(f"已从备份还原 {restored} 个文件")
        return 0

    anim_files, img_files = _scan(ANIM_DIR), _scan(IMG_DIR)
    if not anim_files and not img_files:
        print(f"没有在 {ASSETS} 下找到 webp 资源，检查路径是否正确")
        return 1

    if mode == "dry-run":
        print(f"资源目录: {ASSETS}")
        print(f"  anim/ {len(anim_files)} 个 -> {args.anim_size}px q{args.anim_q}")
        print(f"  img/  {len(img_files)} 个 -> {args.img_size}px q{args.img_q}")
        print()
        # 抽样估算：随机取 min(60, 总数) 个真实压缩一遍看比率
        est: dict[str, tuple[int, int]] = {}
        for label, files, size, q, is_anim in (
            ("anim", anim_files, args.anim_size, args.anim_q, True),
            ("img", img_files, args.img_size, args.img_q, False),
        ):
            if not files:
                continue
            step = max(1, len(files) // 60)
            sample = files[::step][:60]
            before = after = 0
            for p in sample:
                before += p.stat().st_size
                im = Image.open(p)
                frames = []
                for i in range(getattr(im, "n_frames", 1) if is_anim else 1):
                    im.seek(i)
                    frames.append(
                        im.convert("RGBA").resize((size, size), Image.LANCZOS)
                    )
                import io as _io

                buf = _io.BytesIO()
                if len(frames) > 1:
                    frames[0].save(
                        buf,
                        "WEBP",
                        save_all=True,
                        append_images=frames[1:],
                        duration=int(im.info.get("duration") or 100),
                        loop=0,
                        quality=q,
                        alpha_quality=ALPHA_Q,
                        method=6,
                    )
                else:
                    frames[0].save(
                        buf, "WEBP", quality=q, alpha_quality=ALPHA_Q, method=6
                    )
                im.close()
                after += buf.tell()
            est[label] = (before, after)
            ratio = after / before * 100 if before else 100
            total_now = sum(p.stat().st_size for p in files)
            print(
                f"  {label}/  样本 {len(sample)} 个压缩率 {ratio:.0f}%  "
                f"当前 {_human(total_now)} -> 预计 {_human(total_now * ratio / 100)}"
            )
        if est:
            cur = sum(sum(p.stat().st_size for p in fs) for fs in (anim_files, img_files))
            new = sum(
                est[k][0] and (est[k][1] / est[k][0]) or 1
                for k in est
            )
            new_total = (
                sum(p.stat().st_size for p in anim_files) * (est["anim"][1] / est["anim"][0])
                if "anim" in est and est["anim"][0]
                else 0
            ) + (
                sum(p.stat().st_size for p in img_files) * (est["img"][1] / est["img"][0])
                if "img" in est and est["img"][0]
                else 0
            )
            if new_total:
                print()
                print(f"  合计: {_human(cur)} -> {_human(new_total)}  (节省 {_human(cur - new_total)}, {100 - new_total / cur * 100:.0f}%)")
        return 0

    if mode == "verify":
        for label, files in (("anim", anim_files), ("img", img_files)):
            if not files:
                continue
            sizes = [p.stat().st_size for p in files]
            dims = set()
            for p in files[:80]:
                try:
                    with Image.open(p) as im:
                        dims.add(im.size)
                except Exception:
                    pass
            print(
                f"  {label}/  {len(files)} 个  {_human(sum(sizes))}  "
                f"单文件中位 {_human(sorted(sizes)[len(sizes) // 2])}  尺寸 {sorted(dims)}"
            )
        return 0

    # ---- 正式压缩 ----
    if not args.no_backup:
        for sub in ("anim", "img"):
            src_dir = ASSETS / sub
            dst_dir = BACKUP_DIR / sub
            if not src_dir.is_dir():
                continue
            dst_dir.mkdir(parents=True, exist_ok=True)
            for p in src_dir.glob("*.webp"):
                target = dst_dir / p.name
                # 已有备份就保留最初的原始文件，避免重复执行把备份也压糊
                if not target.exists():
                    shutil.copy2(p, target)
        print(f"已备份原始文件到 {BACKUP_DIR}")

    if args.limit:
        anim_files = anim_files[: args.limit]
        img_files = img_files[: args.limit]

    jobs: list[tuple[str, str, int, int, bool]] = []
    for p in anim_files:
        jobs.append((str(p), str(p), args.anim_size, args.anim_q, True))
    for p in img_files:
        jobs.append((str(p), str(p), args.img_size, args.img_q, False))

    total_before = sum(p.stat().st_size for p in anim_files + img_files)
    done_before = 0
    done_after = 0
    skipped = failed = 0
    t0 = time.time()

    with ProcessPoolExecutor(max_workers=_worker_count()) as pool:
        futures = [pool.submit(_compress_one, j) for j in jobs]
        for i, fut in enumerate(as_completed(futures), 1):
            name, before, after, status = fut.result()
            done_before += before
            done_after += after
            if status.startswith("skip"):
                skipped += 1
            elif status.startswith("fail"):
                failed += 1
            if i % 200 == 0 or i == len(jobs):
                pct = done_after / done_before * 100 if done_before else 100
                print(
                    f"  [{i}/{len(jobs)}] {_human(done_before)} -> {_human(done_after)}"
                    f"  ({pct:.0f}%)  跳过 {skipped}  失败 {failed}",
                    flush=True,
                )

    elapsed = time.time() - t0
    print()
    print(f"完成，耗时 {elapsed:.0f}s")
    print(f"  {_human(total_before)} -> {_human(done_after)}  "
          f"(节省 {_human(total_before - done_after)}, {100 - done_after / total_before * 100:.0f}%)")
    print(f"  跳过 {skipped} 个（已达标或无收益）、失败 {failed} 个")
    if failed:
        print("  失败的文件保持原样，可重跑本脚本再试")
    print()
    print("下一步：重新打包 APK 验证包体；不满意可执行 --restore 还原")
    return 0


def main() -> int:
    """解析命令行并分派。"""
    ap = argparse.ArgumentParser(
        description="压缩 assets 下的 webp 资源以减小 APK 体积",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument("--dry-run", action="store_true", help="只估算不修改文件")
    ap.add_argument("--verify", action="store_true", help="查看当前资源尺寸与体积")
    ap.add_argument("--restore", action="store_true", help="从备份还原")
    ap.add_argument("--no-backup", action="store_true", help="跳过备份（不推荐）")
    ap.add_argument("--anim-size", type=int, default=DEFAULTS["anim_size"],
                    help=f"动画目标边长，默认 {DEFAULTS['anim_size']}")
    ap.add_argument("--anim-q", type=int, default=DEFAULTS["anim_q"],
                    help=f"动画质量 0-100，默认 {DEFAULTS['anim_q']}")
    ap.add_argument("--img-size", type=int, default=DEFAULTS["img_size"],
                    help=f"静图目标边长，默认 {DEFAULTS['img_size']}")
    ap.add_argument("--img-q", type=int, default=DEFAULTS["img_q"],
                    help=f"静图质量 0-100，默认 {DEFAULTS['img_q']}")
    ap.add_argument("--limit", type=int, default=0,
                    help="只处理前 N 个文件（样本试跑用，0=全部）")
    args = ap.parse_args()

    if args.restore:
        return run("restore", args)
    if args.verify:
        return run("verify", args)
    if args.dry_run:
        return run("dry-run", args)
    return run("compress", args)


if __name__ == "__main__":
    sys.exit(main())

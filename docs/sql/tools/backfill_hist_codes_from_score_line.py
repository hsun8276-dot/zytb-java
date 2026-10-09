# -*- coding: utf-8 -*-
"""用 score_line 往年官方投档回填 recruit_major_history.hist_recruit_major_id。

因院校/专业代号跨年常变，匹配优先用官方当年代码:
  1. 当前 unv_code + recruit_major_code 命中当年 score_line
  2. 归一化院校名 + 专业名 唯一命中
  3. 归一化院校名 + 专业名 + 投档最低分
  4. 当前 unv_code + 投档最低分(唯一)
  5. 当前 unv_code + 专业名
  6. 归一化院校名 + 专业名去括号后唯一; 多条再用最低分唯一
     (只写空码, 已有 hist_recruit_major_id 不覆盖; 一对多留空)

写入伪 ID: {yy}冀{本|专}{理|文}{当年院校代码}-{当年专业代码}
与 FillServiceImpl.unvCodeOf/majorCodeOf 解析一致。
依据仅 zytb.score_line(河北考试院投档导入), 不抄格学。

用法:
  python backfill_hist_codes_from_score_line.py [--dry-run]
  python backfill_hist_codes_from_score_line.py [--batch=5000] [--sleep=0]

默认大批量写入(本地库); 可重复执行(已正确的跳过)。
"""
import os, sys, re, collections, time
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "crawler"))
from common import db

PROV = "河北"
PLAN_YEAR = 2026
HIST_YEARS = (2022, 2023, 2024, 2025)
# 本地库默认大批量
BATCH_SIZE = 5000
BATCH_SLEEP = 0.0


def norm_name(s):
    s = re.sub(r"\[[^\]]*\]", "", str(s or ""))
    s = re.sub(r"\([^)]*\)", "", s)
    s = re.sub(r"（[^）]*）", "", s)
    return re.sub(r"\s+", "", s)


def major_base(s):
    """去掉专业名括号及之后备注, 作宽松唯一匹配。"""
    s = str(s or "").strip()
    s = re.sub(r"[\(（].*$", "", s)
    return re.sub(r"\s+", "", s)


def make_hist_id(year, batch, subject, unv_code, major_code):
    yy = str(year)[2:]
    b = "本" if batch == "本科批" else "专"
    s = "理" if subject == "物理类" else "文"
    mc = str(major_code).strip()
    if mc.isdigit():
        mc = mc.zfill(2)
    return f"{yy}冀{b}{s}{str(unv_code).zfill(4)}-{mc}"


def pad_code(cc):
    s = str(cc).strip()
    return s.zfill(4) if s.isdigit() else s


def pad_major(mc):
    s = str(mc).strip()
    return s.zfill(2) if s.isdigit() else s


def uniq_pairs(items):
    return list(dict.fromkeys(items))


def parse_args():
    dry = "--dry-run" in sys.argv
    batch = BATCH_SIZE
    sleep_s = BATCH_SLEEP
    for a in sys.argv[1:]:
        if a.startswith("--batch="):
            batch = max(1, int(a.split("=", 1)[1]))
        elif a.startswith("--sleep="):
            sleep_s = max(0.0, float(a.split("=", 1)[1]))
    return dry, batch, sleep_s


def main():
    dry, batch_size, sleep_s = parse_args()
    print(f"write mode: batch={batch_size} sleep={sleep_s}s dry={dry}", flush=True)
    conn = db()
    cur = conn.cursor()

    print("loading score_line indexes...", flush=True)
    cur.execute(
        "SELECT year, batch, subject_type, college_code, major_code, college_name, major_name, min_mark "
        "FROM score_line WHERE province=%s AND year IN %s",
        (PROV, HIST_YEARS),
    )
    by_code = set()  # (y,b,st,unv,maj)
    by_unv_mark = collections.defaultdict(list)  # (y,b,st,unv,mark) -> [(unv,maj)]
    by_unv_mname = collections.defaultdict(list)  # (y,b,st,unv,mname) -> [(unv,maj)]
    by_names = collections.defaultdict(list)  # (y,b,st,norm_unv,mname) -> [(unv,maj,mark)]
    by_base = collections.defaultdict(list)  # (y,b,st,norm_unv,major_base) -> [(unv,maj,mark)]
    n_line = 0
    for y, b, st, cc, mc, cn, mn, mm in cur.fetchall():
        if cc is None or mc is None:
            continue
        n_line += 1
        y = int(y)
        unv = pad_code(cc)
        maj = pad_major(mc)
        mname = str(mn or "").strip()
        pair = (unv, maj)
        by_code.add((y, b, st, unv, maj))
        if mm is not None:
            by_unv_mark[(y, b, st, unv, int(mm))].append(pair)
        if mname:
            by_unv_mname[(y, b, st, unv, mname)].append(pair)
            by_names[(y, b, st, norm_name(cn), mname)].append((unv, maj, mm))
            base = major_base(mname)
            if base:
                by_base[(y, b, st, norm_name(cn), base)].append((unv, maj, mm))
    print(f"  score_line rows: {n_line}", flush=True)

    print("scanning history...", flush=True)
    cur.execute(
        """
        SELECT h.id, h.year, h.hist_recruit_major_id, h.min_mark,
               m.batch, m.subject_type, m.unv_code, m.recruit_major_code,
               m.unv_name, m.major_name
        FROM recruit_major_history h
        JOIN recruit_major m ON m.recruit_major_id = h.recruit_major_id
        WHERE m.province=%s AND m.year=%s AND h.year IN %s
        """,
        (PROV, PLAN_YEAR, HIST_YEARS),
    )
    rows = cur.fetchall()
    print(f"  history rows: {len(rows)}", flush=True)

    updates = []
    stats = collections.Counter()
    by_year_ok = collections.Counter()
    by_year_miss = collections.Counter()

    for hid, hy, old, hmark, batch, subject, unv, mcode, uname, mname in rows:
        hy = int(hy)
        unv4 = pad_code(unv)
        mc2 = pad_major(mcode)
        mname = (mname or "").strip()
        found = None  # (unv, maj)
        how = None

        # 1) exact current codes exist in that year
        if (hy, batch, subject, unv4, mc2) in by_code:
            found = (unv4, mc2)
            how = "code"

        # 2/3) normalized college name + major name (+ mark)
        if found is None and mname:
            cands = by_names.get((hy, batch, subject, norm_name(uname), mname)) or []
            pairs = uniq_pairs((u, m) for u, m, _ in cands)
            if len(pairs) == 1:
                found = pairs[0]
                how = "name"
            elif len(pairs) > 1 and hmark is not None:
                pairs2 = uniq_pairs((u, m) for u, m, mm in cands if mm == int(hmark))
                if len(pairs2) == 1:
                    found = pairs2[0]
                    how = "name_mark"

        # 4) current unv + min_mark unique
        if found is None and hmark is not None:
            pairs = uniq_pairs(by_unv_mark.get((hy, batch, subject, unv4, int(hmark))) or [])
            if len(pairs) == 1:
                found = pairs[0]
                how = "unv_mark"

        # 5) current unv + major name
        if found is None and mname:
            pairs = uniq_pairs(by_unv_mname.get((hy, batch, subject, unv4, mname)) or [])
            if len(pairs) == 1:
                found = pairs[0]
                how = "unv_name"
            elif len(pairs) > 1 and hmark is not None:
                marked = uniq_pairs(
                    p for p in pairs
                    if p in (by_unv_mark.get((hy, batch, subject, unv4, int(hmark))) or [])
                )
                if len(marked) == 1:
                    found = marked[0]
                    how = "unv_name_mark"

        # 6) 归一化校名 + 专业名去括号后唯一; 多条再用最低分唯一
        # 依据: 同年同批同科类 score_line 唯一命中才写; 一对多留空
        if found is None and mname:
            base = major_base(mname)
            if base:
                cands = by_base.get((hy, batch, subject, norm_name(uname), base)) or []
                pairs = uniq_pairs((u, m) for u, m, _ in cands)
                if len(pairs) == 1:
                    found = pairs[0]
                    how = "fuzzy_major"
                elif len(pairs) > 1 and hmark is not None:
                    pairs2 = uniq_pairs((u, m) for u, m, mm in cands if mm == int(hmark))
                    if len(pairs2) == 1:
                        found = pairs2[0]
                        how = "fuzzy_major_mark"

        if found is None:
            stats["miss"] += 1
            by_year_miss[hy] += 1
            continue

        hist_unv, hist_maj = found
        new_id = make_hist_id(hy, batch, subject, hist_unv, hist_maj)
        stats[how] += 1
        by_year_ok[hy] += 1
        if old == new_id:
            stats["already"] += 1
            continue
        # 已有代码的不覆盖; 本轮只补空码(宽松专业名等新策略)
        if old and str(old).strip():
            stats["keep_existing"] += 1
            continue
        updates.append((new_id, hid))

    keys = ("code", "name", "name_mark", "unv_mark", "unv_name", "unv_name_mark",
            "fuzzy_major", "fuzzy_major_mark")
    print("match strategy:", {k: stats[k] for k in keys if stats[k]}, flush=True)
    print(
        f"  matched: {sum(by_year_ok.values())}  miss: {stats['miss']}  "
        f"already: {stats['already']}  to_update: {len(updates)}",
        flush=True,
    )
    print("  ok by year:", dict(sorted(by_year_ok.items())), flush=True)
    print("  miss by year:", dict(sorted(by_year_miss.items())), flush=True)

    if dry:
        print("[dry-run] no write", flush=True)
        conn.close()
        return

    if updates:
        total = len(updates)
        for i in range(0, total, batch_size):
            chunk = updates[i:i + batch_size]
            cur.executemany(
                "UPDATE recruit_major_history SET hist_recruit_major_id=%s WHERE id=%s",
                chunk,
            )
            conn.commit()
            done = min(i + batch_size, total)
            print(f"  committed {done}/{total}", flush=True)
            if sleep_s > 0 and done < total:
                time.sleep(sleep_s)

    cur.execute(
        """
        SELECT h.year,
          SUM(CASE WHEN h.hist_recruit_major_id IS NOT NULL AND h.hist_recruit_major_id<>'' THEN 1 ELSE 0 END) has_id,
          COUNT(*) n
        FROM recruit_major_history h
        JOIN recruit_major m ON m.recruit_major_id=h.recruit_major_id
        WHERE m.province=%s AND m.year=%s AND h.year BETWEEN 2022 AND 2025
        GROUP BY h.year ORDER BY h.year
        """,
        (PROV, PLAN_YEAR),
    )
    print("after:", flush=True)
    for r in cur.fetchall():
        print(" ", r, flush=True)

    cur.execute(
        """
        SELECT h.year, h.hist_recruit_major_id, m.unv_code, m.recruit_major_code, m.major_name, h.min_mark
        FROM recruit_major_history h
        JOIN recruit_major m ON m.recruit_major_id=h.recruit_major_id
        WHERE m.province=%s AND m.year=%s AND h.hist_recruit_major_id IS NOT NULL
        ORDER BY h.year DESC LIMIT 8
        """,
        (PROV, PLAN_YEAR),
    )
    print("samples:", flush=True)
    for r in cur.fetchall():
        print(" ", r, flush=True)

    conn.close()


if __name__ == "__main__":
    main()

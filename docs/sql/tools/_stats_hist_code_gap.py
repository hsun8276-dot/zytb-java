# -*- coding: utf-8 -*-
"""2022-2025 历史院校/专业代码缺口 dry-run 统计(不写库)。

依据: 仅对照本库 score_line(河北官方投档导入结果)。
唯一命中才计入「可补」; 一对多不计。
"""
import collections
import json
import os
import re
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "crawler"))
from common import db

PROV = "河北"
PLAN_YEAR = 2026
HIST_YEARS = (2022, 2023, 2024, 2025)
OUT = os.path.join(os.path.dirname(__file__), "data", "_hist_code_gap_stats.json")


def norm_unv(s):
    s = re.sub(r"\[[^\]]*\]", "", str(s or ""))
    s = re.sub(r"\([^)]*\)", "", s)
    s = re.sub(r"（[^）]*）", "", s)
    return re.sub(r"\s+", "", s)


def major_base(s):
    """去掉专业名括号及之后备注, 作宽松匹配。"""
    s = str(s or "").strip()
    s = re.sub(r"[\(（].*$", "", s)
    return re.sub(r"\s+", "", s)


def pad_code(cc):
    s = str(cc).strip()
    return s.zfill(4) if s.isdigit() else s


def pad_major(mc):
    s = str(mc).strip()
    return s.zfill(2) if s.isdigit() else s


def uniq_pairs(items):
    return list(dict.fromkeys(items))


def main():
    conn = db()
    cur = conn.cursor()

    cur.execute(
        "SELECT year, batch, subject_type, college_code, major_code, college_name, major_name, min_mark "
        "FROM score_line WHERE province=%s AND year IN %s",
        (PROV, HIST_YEARS),
    )
    by_code = set()
    by_names = collections.defaultdict(list)  # (y,b,st,nunv,mname) -> [(unv,maj,mark,cn,mn)]
    by_base = collections.defaultdict(list)  # (y,b,st,nunv,base) -> [(unv,maj,mark,cn,mn)]
    by_unv_exists = set()  # (y,b,st,nunv)
    n_line = 0
    for y, b, st, cc, mc, cn, mn, mm in cur.fetchall():
        if cc is None or mc is None:
            continue
        n_line += 1
        y = int(y)
        unv = pad_code(cc)
        maj = pad_major(mc)
        mname = str(mn or "").strip()
        nunv = norm_unv(cn)
        by_code.add((y, b, st, unv, maj))
        by_unv_exists.add((y, b, st, nunv))
        by_names[(y, b, st, nunv, mname)].append((unv, maj, mm, str(cn), mname))
        base = major_base(mname)
        if base:
            by_base[(y, b, st, nunv, base)].append((unv, maj, mm, str(cn), mname))

    cur.execute(
        """
        SELECT h.id, h.year, h.hist_recruit_major_id, h.min_mark,
               m.batch, m.subject_type, m.unv_code, m.recruit_major_code,
               m.unv_name, m.major_name, m.recruit_major_id
        FROM recruit_major_history h
        JOIN recruit_major m ON m.recruit_major_id = h.recruit_major_id
        WHERE m.province=%s AND m.year=%s AND h.year IN %s
        """,
        (PROV, PLAN_YEAR, HIST_YEARS),
    )
    rows = cur.fetchall()

    total = collections.Counter()
    miss = collections.Counter()
    miss_reason = collections.Counter()
    can_fuzzy = collections.Counter()
    evidence = {y: [] for y in HIST_YEARS}
    evidence_no = {y: [] for y in HIST_YEARS}

    for hid, hy, old, hmark, batch, subject, unv, mcode, uname, mname, rid in rows:
        hy = int(hy)
        total[hy] += 1
        has_id = bool(old and str(old).strip())
        if has_id:
            continue
        miss[hy] += 1
        unv4 = pad_code(unv)
        mc2 = pad_major(mcode)
        mname = (mname or "").strip()
        nunv = norm_unv(uname)

        # 与现网回填脚本同口径的「当前已会命中」检查(理论上缺码行不应再命中)
        if (hy, batch, subject, unv4, mc2) in by_code:
            miss_reason[(hy, "exact_code_should_exist")] += 1
            continue

        cands = by_names.get((hy, batch, subject, nunv, mname)) or []
        pairs = uniq_pairs((u, m) for u, m, *_ in cands)
        if len(pairs) == 1:
            miss_reason[(hy, "exact_name_should_exist")] += 1
            continue
        if len(pairs) > 1:
            if hmark is not None:
                pairs2 = uniq_pairs((u, m) for u, m, mm, *_ in cands if mm == int(hmark))
                if len(pairs2) == 1:
                    miss_reason[(hy, "exact_name_mark_should_exist")] += 1
                    continue
            miss_reason[(hy, "exact_name_multi")] += 1
            if len(evidence_no[hy]) < 5:
                evidence_no[hy].append({
                    "reason": "exact_name_multi",
                    "hist": {"year": hy, "batch": batch, "subject": subject,
                             "unv_name": uname, "major_name": mname, "min_mark": hmark,
                             "plan_unv_code": unv4, "plan_major_code": mc2, "rid": rid},
                    "score_line_cands": [
                        {"college_code": u, "major_code": m, "min_mark": mm,
                         "college_name": cn, "major_name": mn}
                        for u, m, mm, cn, mn in cands[:8]
                    ],
                })
            continue

        # 校名是否存在于当年投档
        if (hy, batch, subject, nunv) not in by_unv_exists:
            miss_reason[(hy, "no_college_in_score_line")] += 1
            if len(evidence_no[hy]) < 8:
                evidence_no[hy].append({
                    "reason": "no_college_in_score_line",
                    "hist": {"year": hy, "batch": batch, "subject": subject,
                             "unv_name": uname, "norm_unv": nunv, "major_name": mname,
                             "min_mark": hmark, "rid": rid},
                    "basis": "score_line 同年同批同科类下, 归一化校名无此校",
                })
            continue

        # 宽松专业名
        base = major_base(mname)
        fcands = by_base.get((hy, batch, subject, nunv, base)) or []
        fpairs = uniq_pairs((u, m) for u, m, *_ in fcands)
        if len(fpairs) == 1:
            can_fuzzy[hy] += 1
            miss_reason[(hy, "fuzzy_major_unique")] += 1
            u, m = fpairs[0]
            hit = next(x for x in fcands if x[0] == u and x[1] == m)
            if len(evidence[hy]) < 8:
                evidence[hy].append({
                    "reason": "fuzzy_major_unique",
                    "basis": "同年同批同科类 + 归一化校名相同 + 专业名去括号后相同, 且唯一",
                    "hist": {"year": hy, "unv_name": uname, "major_name": mname,
                             "major_base": base, "min_mark": hmark, "rid": rid},
                    "score_line_hit": {
                        "college_code": hit[0], "major_code": hit[1], "min_mark": hit[2],
                        "college_name": hit[3], "major_name": hit[4],
                    },
                    "would_write": f"{str(hy)[2:]}冀"
                                   f"{'本' if batch=='本科批' else '专'}"
                                   f"{'理' if subject=='物理类' else '文'}"
                                   f"{hit[0]}-{hit[1]}",
                })
            continue

        if len(fpairs) > 1:
            if hmark is not None:
                fp2 = uniq_pairs((u, m) for u, m, mm, *_ in fcands if mm == int(hmark))
                if len(fp2) == 1:
                    can_fuzzy[hy] += 1
                    miss_reason[(hy, "fuzzy_major_mark_unique")] += 1
                    u, m = fp2[0]
                    hit = next(x for x in fcands if x[0] == u and x[1] == m and x[2] == int(hmark))
                    if len(evidence[hy]) < 8:
                        evidence[hy].append({
                            "reason": "fuzzy_major_mark_unique",
                            "basis": "宽松专业名多条, 再用历史最低分唯一命中 score_line.min_mark",
                            "hist": {"year": hy, "unv_name": uname, "major_name": mname,
                                     "major_base": base, "min_mark": hmark, "rid": rid},
                            "score_line_hit": {
                                "college_code": hit[0], "major_code": hit[1], "min_mark": hit[2],
                                "college_name": hit[3], "major_name": hit[4],
                            },
                        })
                    continue
            miss_reason[(hy, "fuzzy_major_multi")] += 1
            if len(evidence_no[hy]) < 8:
                evidence_no[hy].append({
                    "reason": "fuzzy_major_multi",
                    "basis": "宽松专业名后仍多条, 不唯一, 不计可补",
                    "hist": {"year": hy, "unv_name": uname, "major_name": mname,
                             "major_base": base, "min_mark": hmark, "rid": rid},
                    "score_line_cands": [
                        {"college_code": u, "major_code": m, "min_mark": mm,
                         "college_name": cn, "major_name": mn}
                        for u, m, mm, cn, mn in fcands[:8]
                    ],
                })
            continue

        miss_reason[(hy, "college_ok_major_still_miss")] += 1
        if len(evidence_no[hy]) < 8:
            evidence_no[hy].append({
                "reason": "college_ok_major_still_miss",
                "basis": "当年投档有该校, 精确/宽松专业名均无唯一命中",
                "hist": {"year": hy, "unv_name": uname, "major_name": mname,
                         "major_base": base, "min_mark": hmark, "rid": rid},
            })

    report = {
        "source": {
            "score_line": "zytb.score_line, province=河北, year=2022..2025 (考试院投档导入)",
            "history": "zytb.recruit_major_history JOIN recruit_major year=2026",
            "rule": "只统计相对 score_line 的唯一命中; 一对多不算可补; 本脚本不写库",
        },
        "score_line_rows_indexed": n_line,
        "by_year": {},
        "miss_reason_counts": {
            f"{y}:{k}": v for (y, k), v in sorted(miss_reason.items())
        },
        "evidence_can_fill_samples": evidence,
        "evidence_cannot_fill_samples": evidence_no,
    }
    for y in HIST_YEARS:
        m = miss[y]
        fuzzy = can_fuzzy[y]
        report["by_year"][str(y)] = {
            "history_total": total[y],
            "already_has_code": total[y] - m,
            "missing_code": m,
            "can_fill_by_fuzzy_major_unique": fuzzy,
            "still_missing_if_only_fuzzy": m - fuzzy,
            "fill_rate_of_missing_pct": round(100.0 * fuzzy / m, 2) if m else 0,
        }

    report["totals"] = {
        "history_2022_2025": sum(total.values()),
        "missing_code": sum(miss.values()),
        "can_fill_fuzzy_unique": sum(can_fuzzy.values()),
        "still_missing_after_fuzzy": sum(miss.values()) - sum(can_fuzzy.values()),
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print("=== 依据 ===")
    print(report["source"]["score_line"])
    print(report["source"]["history"])
    print(report["source"]["rule"])
    print("score_line indexed:", n_line)
    print()
    print("=== 分年 ===")
    for y in HIST_YEARS:
        print(y, report["by_year"][str(y)])
    print()
    print("=== 合计 ===")
    print(report["totals"])
    print()
    print("=== 缺码原因计数(有依据分类) ===")
    for k, v in sorted(report["miss_reason_counts"].items()):
        print(f"  {k}: {v}")
    print()
    print("written:", OUT)


if __name__ == "__main__":
    main()

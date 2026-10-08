# -*- coding: utf-8 -*-
"""用格学抓包的 MajorGrade/MajorRank 成对回填 recruit_major。
college_data 无此成对字段；源站 GetRecommendMajors 有。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "crawler"))
from common import db

SRC = Path(r"C:\Users\EDY\.cursor\browser-logs\cdp-response-Runtime.evaluate-2026-10-08T14-37-21-250Z.json")


def load_map():
    raw = json.loads(SRC.read_text(encoding="utf-8"))
    # CDP wrapper: {"result":{"type":"string","value":"..."}}
    val = raw
    if isinstance(raw, dict) and "result" in raw:
        val = raw["result"].get("value", raw["result"])
    if isinstance(val, str):
        val = json.loads(val)
    merged = {}
    for k in ("c0", "c1", "c2"):
        part = val.get(k) or {}
        for rid, gr in part.items():
            if isinstance(gr, (list, tuple)) and len(gr) >= 2:
                merged[rid] = (str(gr[0]).strip(), int(gr[1]))
            elif isinstance(gr, dict):
                merged[rid] = (str(gr["grade"]).trim() if False else str(gr.get("grade")).strip(), int(gr["rank"]))
    print(f"loaded pairs: {len(merged)} (reported total={val.get('total')})")
    return merged


def main():
    pairs = load_map()
    conn = db()
    cur = conn.cursor()

    # current coverage
    cur.execute(
        "SELECT SUM(major_grade IS NOT NULL AND major_grade<>'' AND major_rank IS NOT NULL AND major_rank<>0), "
        "SUM(major_grade IS NOT NULL AND major_grade<>''), "
        "SUM(major_rank IS NOT NULL AND major_rank<>0), COUNT(*) "
        "FROM recruit_major WHERE province=%s AND year=2026",
        ("河北",),
    )
    print("before both/grade/rank/total:", cur.fetchone())

    updated = 0
    matched = 0
    conflict = 0
    samples = []
    for rid, (grade, rank) in pairs.items():
        cur.execute(
            "SELECT major_grade, major_rank FROM recruit_major WHERE recruit_major_id=%s",
            (rid,),
        )
        row = cur.fetchone()
        if not row:
            continue
        matched += 1
        old_g, old_r = row
        old_g = (old_g or "").strip() or None
        old_r = int(old_r) if old_r not in (None, 0, "0") else None
        if old_g == grade and old_r == rank:
            continue
        if old_g and old_r and (old_g != grade or old_r != rank):
            conflict += 1
            if len(samples) < 5:
                samples.append((rid, old_g, old_r, grade, rank))
        cur.execute(
            "UPDATE recruit_major SET major_grade=%s, major_rank=%s WHERE recruit_major_id=%s",
            (grade, rank, rid),
        )
        updated += 1

    conn.commit()
    cur.execute(
        "SELECT SUM(major_grade IS NOT NULL AND major_grade<>'' AND major_rank IS NOT NULL AND major_rank<>0), "
        "SUM(major_grade IS NOT NULL AND major_grade<>''), "
        "SUM(major_rank IS NOT NULL AND major_rank<>0), COUNT(*) "
        "FROM recruit_major WHERE province=%s AND year=2026",
        ("河北",),
    )
    print("after both/grade/rank/total:", cur.fetchone())
    print(f"matched ids={matched}, updated={updated}, conflicts(overwritten)={conflict}")
    if samples:
        print("conflict samples:", samples)

    # how many scraped pairs not in our recruit table
    missing = 0
    for rid in pairs:
        cur.execute("SELECT 1 FROM recruit_major WHERE recruit_major_id=%s", (rid,))
        if not cur.fetchone():
            missing += 1
    print(f"scraped ids not in recruit_major: {missing}")
    conn.close()

    # save map for reuse
    out = Path(__file__).parent / "data" / "gexue_major_grade_rank.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({k: {"grade": g, "rank": r} for k, (g, r) in pairs.items()},
                              ensure_ascii=False, indent=0), encoding="utf-8")
    print("saved", out)


if __name__ == "__main__":
    main()

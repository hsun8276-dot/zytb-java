# -*- coding: utf-8 -*-
"""评级/排名准确性清洗:
1. 只保留成对数据(grade+rank 都有且 rank>0)；缺一清成空
2. 若有格学抓包成对数据，以源站为准覆盖
3. Excel 仅在源站无该 ID 时保留其成对值
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "crawler"))
from common import db

GEXUE = Path(__file__).parent / "data" / "gexue_major_grade_rank.json"


def main():
    conn = db()
    cur = conn.cursor()

    gexue = {}
    if GEXUE.exists():
        raw = json.loads(GEXUE.read_text(encoding="utf-8"))
        for rid, v in raw.items():
            g = str(v.get("grade") or "").strip()
            r = v.get("rank")
            try:
                r = int(r)
            except (TypeError, ValueError):
                r = None
            if g and r and r > 0:
                gexue[rid] = (g, r)
    print(f"gexue pairs: {len(gexue)}")

    cur.execute(
        "SELECT recruit_major_id, major_grade, major_rank FROM recruit_major "
        "WHERE province=%s AND year=2026",
        ("河北",),
    )
    rows = cur.fetchall()

    clear = 0
    set_from_gexue = 0
    keep_excel_pair = 0
    updates = []

    for rid, grade, rank in rows:
        g = (grade or "").strip() or None
        try:
            r = int(rank) if rank is not None else None
        except (TypeError, ValueError):
            r = None
        if r == 0:
            r = None

        if rid in gexue:
            ng, nr = gexue[rid]
            if g != ng or r != nr:
                updates.append((ng, nr, rid))
                set_from_gexue += 1
            else:
                keep_excel_pair += 1  # already matches
            continue

        # 无源站依据：仅保留已成对的 Excel 值，否则清空
        if g and r:
            keep_excel_pair += 1
            continue
        if g or r:
            updates.append((None, None, rid))
            clear += 1

    print(f"will update: {len(updates)} (from_gexue={set_from_gexue}, clear_partial={clear})")
    print(f"already ok / keep excel pair: {keep_excel_pair}")

    if updates:
        cur.executemany(
            "UPDATE recruit_major SET major_grade=%s, major_rank=%s WHERE recruit_major_id=%s",
            updates,
        )
        conn.commit()

    cur.execute(
        """
        SELECT
          SUM(major_grade IS NOT NULL AND major_grade<>'' AND major_rank IS NOT NULL AND major_rank<>0) AS both_ok,
          SUM(major_grade IS NOT NULL AND major_grade<>'' AND (major_rank IS NULL OR major_rank=0)) AS grade_only,
          SUM((major_grade IS NULL OR major_grade='') AND major_rank IS NOT NULL AND major_rank<>0) AS rank_only,
          SUM((major_grade IS NULL OR major_grade='') AND (major_rank IS NULL OR major_rank=0)) AS none_ok,
          COUNT(*)
        FROM recruit_major WHERE province=%s AND year=2026
        """,
        ("河北",),
    )
    print("after both/grade_only/rank_only/none/total:", cur.fetchone())
    conn.close()


if __name__ == "__main__":
    main()

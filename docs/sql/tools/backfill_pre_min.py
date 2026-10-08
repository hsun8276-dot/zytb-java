# -*- coding: utf-8 -*-
"""用 score_line【当年】官方投档回填 recruit_major.pre_min_*。
无当年官网投档则保持 NULL，禁止用往年或其他字段兜底。
"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "crawler"))
from common import db

PROV = "河北"
YEAR = 2026


def load_rank_tables(conn):
    cur = conn.cursor()
    cur.execute(
        "SELECT subject_type, score, cumulative FROM score_ranking "
        "WHERE province=%s AND year=%s ORDER BY subject_type, score DESC",
        (PROV, YEAR),
    )
    by_subj = {}
    for st, sc, cum in cur.fetchall():
        by_subj.setdefault(st, []).append((sc, cum))

    cur.execute(
        "SELECT subject_type, batch, score FROM control_line "
        "WHERE province=%s AND year=%s",
        (PROV, YEAR),
    )
    ctrl = {(st, b): m for st, b, m in cur.fetchall()}
    return by_subj, ctrl


def rank_of(by_subj, subject, mark):
    if mark is None:
        return None
    rows = by_subj.get(subject) or []
    for sc, cum in rows:
        if sc <= mark:
            return cum
    return rows[-1][1] if rows else None


def pct_of(by_subj, ctrl, subject, batch, rnk):
    if rnk is None:
        return None
    rows = by_subj.get(subject) or []
    if not rows:
        return None
    total = rows[-1][1]
    cm = ctrl.get((subject, batch))
    if cm is not None:
        for sc, cum in rows:
            if sc <= cm:
                total = cum
                break
    if not total:
        return None
    return round(rnk * 100.0 / total, 4)


def main():
    conn = db()
    cur = conn.cursor()
    by_subj, ctrl = load_rank_tables(conn)

    official = {}
    cur.execute(
        "SELECT batch, subject_type, college_code, major_code, min_mark "
        "FROM score_line WHERE province=%s AND year=%s",
        (PROV, YEAR),
    )
    for b, st, cc, mc, mm in cur.fetchall():
        official[(b, st, str(cc).zfill(4), str(mc).zfill(2))] = mm

    cur.execute(
        "SELECT id, batch, subject_type, unv_code, recruit_major_code, pre_min_mark "
        "FROM recruit_major WHERE province=%s AND year=%s",
        (PROV, YEAR),
    )
    rows = cur.fetchall()

    updates = []
    ok = fix = still = 0
    for rid, batch, subject, unv, mcode, old in rows:
        key = (batch, subject, str(unv).zfill(4), str(mcode).zfill(2))
        mark = official.get(key)
        if mark is None:
            still += 1
            continue
        if old is not None and int(old) == int(mark):
            ok += 1
            continue
        rnk = rank_of(by_subj, subject, mark)
        p = pct_of(by_subj, ctrl, subject, batch, rnk)
        updates.append((mark, rnk, p, rid))
        fix += 1

    print(f"recruit_major {YEAR}: {len(rows)}")
    print(f"  match {YEAR} official: {ok}")
    print(f"  sync from {YEAR} official: {fix}")
    print(f"  leave NULL (no {YEAR} official): {still}")

    if updates:
        cur.executemany(
            "UPDATE recruit_major SET pre_min_mark=%s, pre_min_rank=%s, "
            "pre_min_percentile=%s WHERE id=%s",
            updates,
        )
        conn.commit()
        print(f"updated {len(updates)} rows")

    conn.close()


if __name__ == "__main__":
    main()

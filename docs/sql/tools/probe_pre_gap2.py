# -*- coding: utf-8 -*-
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "crawler"))
from common import db

conn = db()
cur = conn.cursor()
cur.execute("SHOW COLUMNS FROM score_line")
print("cols", [r[0] for r in cur.fetchall()])

cur.execute(
    "SELECT * FROM score_line WHERE province=%s AND year=2026 AND batch=%s AND subject_type=%s LIMIT 1",
    ("河北", "本科批", "物理类"),
)
print("sample row", cur.fetchone())
print("desc", [d[0] for d in cur.description])

cur.execute(
    "SELECT unv_code, recruit_major_code, unv_name, major_name, first_time_admission "
    "FROM recruit_major WHERE province=%s AND year=2026 AND pre_min_mark IS NULL "
    "AND batch=%s AND subject_type=%s LIMIT 8",
    ("河北", "本科批", "物理类"),
)
nulls = cur.fetchall()
print("null samples:")
for r in nulls:
    print(" ", r)
    u, m = str(r[0]).zfill(4), str(r[1]).zfill(2)
    cur.execute(
        "SELECT college_code, major_code, min_mark, college_name, major_name FROM score_line "
        "WHERE province=%s AND year=2026 AND batch=%s AND subject_type=%s "
        "AND LPAD(CAST(college_code AS CHAR),4,'0')=%s AND LPAD(major_code,2,'0')=%s LIMIT 3",
        ("河北", "本科批", "物理类", u, m),
    )
    print("   score_line hit:", cur.fetchall())

# how many nulls exist in score_line at all for same codes
cur.execute(
    """
    SELECT COUNT(*) FROM recruit_major m
    WHERE m.province=%s AND m.year=2026 AND m.pre_min_mark IS NULL
      AND EXISTS (
        SELECT 1 FROM score_line s
        WHERE s.province=m.province AND s.year=m.year AND s.batch=m.batch
          AND s.subject_type=m.subject_type
          AND LPAD(CAST(s.college_code AS CHAR),4,'0')=LPAD(m.unv_code,4,'0')
          AND LPAD(s.major_code,2,'0')=LPAD(m.recruit_major_code,2,'0')
      )
    """,
    ("河北",),
)
print("nulls that DO have score_line same year:", cur.fetchone()[0])

cur.execute(
    """
    SELECT COUNT(*) FROM recruit_major m
    WHERE m.province=%s AND m.year=2026 AND m.pre_min_mark IS NULL
      AND NOT EXISTS (
        SELECT 1 FROM score_line s
        WHERE s.province=m.province AND s.year=m.year AND s.batch=m.batch
          AND s.subject_type=m.subject_type
          AND LPAD(CAST(s.college_code AS CHAR),4,'0')=LPAD(m.unv_code,4,'0')
          AND LPAD(s.major_code,2,'0')=LPAD(m.recruit_major_code,2,'0')
      )
    """,
    ("河北",),
)
print("nulls with NO score_line same year:", cur.fetchone()[0])

# 2024 missing?
cur.execute("SELECT year, COUNT(*) FROM score_line WHERE province=%s GROUP BY year", ("河北",))
print("years", cur.fetchall())
conn.close()

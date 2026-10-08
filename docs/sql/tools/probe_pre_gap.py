# -*- coding: utf-8 -*-
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "crawler"))
from common import db

conn = db()
cur = conn.cursor()
print("=== score_line coverage ===")
cur.execute(
    "SELECT year, batch, subject_type, COUNT(*) n FROM score_line "
    "WHERE province=%s GROUP BY year,batch,subject_type ORDER BY year DESC, batch, subject_type",
    ("河北",),
)
for r in cur.fetchall():
    print(r)

print("=== recruit 2026 null pre_min ===")
cur.execute(
    "SELECT batch, subject_type, COUNT(*) n, "
    "SUM(CASE WHEN pre_min_mark IS NULL THEN 1 ELSE 0 END) nulls "
    "FROM recruit_major WHERE province=%s AND year=2026 GROUP BY batch, subject_type",
    ("河北",),
)
for r in cur.fetchall():
    print(r)

print("=== why null: match score_line by codes? ===")
cur.execute(
    """
    SELECT COUNT(*) total_null,
           SUM(CASE WHEN s.id IS NOT NULL THEN 1 ELSE 0 END) has_line_same_year,
           SUM(CASE WHEN s.id IS NULL THEN 1 ELSE 0 END) no_line
    FROM recruit_major m
    LEFT JOIN score_line s
      ON s.province=m.province AND s.year=m.year AND s.batch=m.batch
     AND s.subject_type=m.subject_type
     AND s.unv_code=m.unv_code AND s.major_code=m.recruit_major_code
    WHERE m.province=%s AND m.year=2026 AND m.pre_min_mark IS NULL
    """,
    ("河北",),
)
print(cur.fetchone())

print("=== null sample ===")
cur.execute(
    "SELECT recruit_major_id, unv_code, recruit_major_code, unv_name, major_name, batch, subject_type, first_time_admission "
    "FROM recruit_major WHERE province=%s AND year=2026 AND pre_min_mark IS NULL LIMIT 10",
    ("河北",),
)
for r in cur.fetchall():
    print(r)

print("=== first_time among null ===")
cur.execute(
    "SELECT SUM(CASE WHEN first_time_admission=1 THEN 1 ELSE 0 END), COUNT(*) "
    "FROM recruit_major WHERE province=%s AND year=2026 AND pre_min_mark IS NULL",
    ("河北",),
)
print("first_time / null_total", cur.fetchone())
conn.close()

# -*- coding: utf-8 -*-
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "crawler"))
from common import db

ids = ["26冀本理2762-52", "26冀本理2557-12", "26冀本理2413-04", "26冀本理2439-22"]
# sample: PreMin/PreAvg
sample = {
    "26冀本理2762-52": (471, 471),
    "26冀本理2557-12": (451, 455),
    "26冀本理2413-04": (450, 454),
    "26冀本理2439-22": (443, 444),
}
conn = db()
cur = conn.cursor()
for rid in ids:
    cur.execute(
        "SELECT pre_min_mark, pre_avg_mark, ref_mark, ref_rank FROM recruit_major WHERE recruit_major_id=%s",
        (rid,),
    )
    print(rid, "db", cur.fetchone(), "sample", sample[rid])

cur.execute(
    "SELECT SUM(pre_min_mark IS NULL), "
    "SUM(pre_min_mark IS NULL AND ref_mark IS NOT NULL), "
    "SUM(pre_min_mark IS NOT NULL) "
    "FROM recruit_major WHERE province=%s AND year=2026",
    ("河北",),
)
print("null_pre / null_pre_with_ref / has_pre =", cur.fetchone())
conn.close()

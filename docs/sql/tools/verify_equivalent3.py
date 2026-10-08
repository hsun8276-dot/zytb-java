"""在 '百分位 = 1 - 位次/本科线上累计人数' 口径下, 枚举 2026 侧的位次->分数映射方式."""
import bisect
import json
import math
import sys
import openpyxl

from verify_equivalent import API_DIR, RANK_XLSX, load_tables

sys.stdout.reconfigure(encoding="utf-8")

wb = openpyxl.load_workbook(RANK_XLSX, read_only=True, data_only=True)
print("分数线表(前 20 行非空):")
for r in list(wb["分数线"].iter_rows(values_only=True))[4:24]:
    print("  ", [v for v in r[:14]])

tables = load_tables()
samples = []
for name in ("GetRecommendMajors", "GetUserAspMajors", "GetDebutMajors"):
    with open(f"{API_DIR}\\{name}.json", encoding="utf-8-sig") as f:
        for rec in json.loads(json.load(f)["Data"]):
            for m in rec.get("MajorHistorys") or []:
                if m.get("MinMarkRank") and m.get("MinMarkPercentile") and m.get("EquivalentMinMark"):
                    samples.append(m)

t = tables[(2026, "physics")]
cums = [c for _, _, c in t]
scores = [s for s, _, _ in t]
n26 = dict((s, c) for s, _, c in t)[444]


def first_ge(rank):
    i = bisect.bisect_left(cums, rank)
    return scores[min(i, len(scores) - 1)]


def first_gt(rank):
    i = bisect.bisect_right(cums, rank)
    return scores[min(i, len(scores) - 1)]


def interp(rank):
    """分数档内按累计人数线性插值后四舍五入."""
    i = bisect.bisect_left(cums, rank)
    if i == 0:
        return scores[0]
    lo_c, hi_c = cums[i - 1], cums[i]
    frac = (rank - lo_c) / (hi_c - lo_c)
    return round(scores[i - 1] - frac)


variants = {
    "first_ge(round)": lambda r: first_ge(round(r)),
    "first_ge(ceil)": lambda r: first_ge(math.ceil(r)),
    "first_ge(floor)": lambda r: first_ge(math.floor(r)),
    "first_gt(round)": lambda r: first_gt(round(r)),
    "first_gt(floor)": lambda r: first_gt(math.floor(r)),
    "interp": interp,
}
for name, fn in variants.items():
    miss = [(m["Year"], m["MinMarkRank"], m["MinMarkPercentile"], m["EquivalentMinMark"], fn((1 - m["MinMarkPercentile"] / 100) * n26))
            for m in samples if fn((1 - m["MinMarkPercentile"] / 100) * n26) != m["EquivalentMinMark"]]
    print(f"{name:<18} 命中 {len(samples) - len(miss)}/{len(samples)}  未命中样例 {miss[:4]}")

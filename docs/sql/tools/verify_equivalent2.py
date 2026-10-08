"""反推格学百分位的分母 N, 并据此验证等效分."""
import json
import statistics
import sys

from verify_equivalent import API_DIR, load_tables, score_at_rank

sys.stdout.reconfigure(encoding="utf-8")

tables = load_tables()
samples = []
for name in ("GetRecommendMajors", "GetUserAspMajors", "GetDebutMajors"):
    with open(f"{API_DIR}\\{name}.json", encoding="utf-8-sig") as f:
        for rec in json.loads(json.load(f)["Data"]):
            for m in rec.get("MajorHistorys") or []:
                if m.get("MinMarkRank") and m.get("MinMarkPercentile") and m.get("EquivalentMinMark"):
                    samples.append(m)

n_by_year = {}
for y in sorted({m["Year"] for m in samples}):
    ns = [m["MinMarkRank"] / (1 - m["MinMarkPercentile"] / 100) for m in samples if m["Year"] == y]
    n = statistics.median(ns)
    n_by_year[y] = n
    t = tables[(y, "physics")]
    near = min(t, key=lambda x: abs(x[2] - n))
    print(f"{y}: N≈{n:.0f} (min {min(ns):.0f} max {max(ns):.0f}), 一分一段中累计最接近的分数档 {near}")

# 2026 的 N 未知: 用各年 N 对应分数档的口径(同一条线)去 2026 表里找, 这里先枚举 2026 每个分数档的累计人数作为 N, 看哪个命中最多
target = tables[(2026, "physics")]
best = []
for score, _, n26 in target:
    if score < 300 or score > 520:
        continue
    hit = sum(score_at_rank(target, (1 - m["MinMarkPercentile"] / 100) * n26) == m["EquivalentMinMark"] for m in samples)
    best.append((hit, score, n26))
best.sort(reverse=True)
print("\n2026 分母取不同分数档累计人数时的命中数(前5):", best[:5], "样本", len(samples))

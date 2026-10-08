"""用河北一分一段表验证格学(gexuejy)历史等效分的换算口径."""
import bisect
import json
import re
import sys
import openpyxl

sys.stdout.reconfigure(encoding="utf-8")

RANK_XLSX = r"F:\教育考试院\河北省\一分一档数据\2021-2026年 河北高考一分一档表.xlsx"
API_DIR = r"F:\hbbxl\docs\reference_site\api"


def load_tables():
    """返回 {(year, 'physics'|'history'): [(score, count, cum), ...] 按分数降序}."""
    wb = openpyxl.load_workbook(RANK_XLSX, read_only=True, data_only=True)
    tables = {}
    for ws in wb.worksheets:
        if not ws.title.isdigit():
            continue
        year = int(ws.title)
        phy, his = [], []
        for r in list(ws.iter_rows(values_only=True))[2:]:
            if r[0] is None:
                continue
            score = int(re.match(r"\d+", str(r[0])).group())
            if r[2] is not None:
                phy.append((score, r[1], r[2]))
            if r[4] is not None:
                his.append((score, r[3], r[4]))
        tables[(year, "physics")] = phy
        tables[(year, "history")] = his
    return tables


def score_at_rank(table, rank):
    """累计人数首次 >= rank 的分数(同位次分)."""
    cums = [c for _, _, c in table]
    i = bisect.bisect_left(cums, rank)
    return table[min(i, len(table) - 1)][0]


def main():
    tables = load_tables()
    for k, t in sorted(tables.items()):
        print(k, "档数", len(t), "最高", t[0], "总人数(140分)", t[-1][2])

    samples = []
    for name in ("GetRecommendMajors", "GetUserAspMajors", "GetDebutMajors"):
        with open(f"{API_DIR}\\{name}.json", encoding="utf-8-sig") as f:
            for rec in json.loads(json.load(f)["Data"]):
                for m in rec.get("MajorHistorys") or []:
                    if m.get("MinMarkRank") and m.get("EquivalentMinMark"):
                        samples.append(m)
    print("\n样本(物理组)", len(samples))

    target = tables[(2026, "physics")]
    total26 = target[-1][2]
    hit_rank = hit_pct = 0
    diffs = []
    for m in samples:
        src = tables[(m["Year"], "physics")]
        total = src[-1][2]
        by_rank = score_at_rank(target, m["MinMarkRank"])
        by_pct = score_at_rank(target, round(m["MinMarkRank"] / total * total26))
        hit_rank += by_rank == m["EquivalentMinMark"]
        hit_pct += by_pct == m["EquivalentMinMark"]
        if len(diffs) < 8:
            diffs.append((m["Year"], m["MinMark"], m["MinMarkRank"], m["MinMarkPercentile"],
                          "格学", m["EquivalentMinMark"], "同位次", by_rank, "同百分位", by_pct))
    for d in diffs:
        print(d)
    print(f"\n同位次命中 {hit_rank}/{len(samples)}  同百分位命中 {hit_pct}/{len(samples)}")


if __name__ == "__main__":
    main()

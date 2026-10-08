import json
import sys
import openpyxl

sys.stdout.reconfigure(encoding="utf-8")

XLSX = r"F:\教育考试院\河北省\2026年河北本科物理组志愿数据.xlsx"
API = r"F:\hbbxl\docs\reference_site\api\GetRecommendMajors.json"

wb = openpyxl.load_workbook(XLSX, read_only=True, data_only=True)
rows = list(wb.worksheets[0].iter_rows(values_only=True))
h = {name: i for i, name in enumerate(rows[0])}
index = {}
for r in rows[1:]:
    key = (str(r[h["院校代码"]]).zfill(4), str(r[h["专业代码"]]).zfill(2))
    index.setdefault(key, []).append(r)

with open(API, encoding="utf-8-sig") as f:
    recs = json.loads(json.load(f)["Data"])

cols = ["26人", "26参考分", "26参考位", "同位分", "25分", "25位", "24分", "24位", "23分", "23位", "学费", "学制"]
hit = miss = 0
stats = {"PreMinMark==26参考分": 0, "25等效==同位分": 0, "plan==26人": 0, "tuition==学费": 0}
for g in recs:
    key = (g["UnvCode"], g["RecruitMajorCode"])
    found = index.get(key)
    if not found:
        miss += 1
        continue
    hit += 1
    r = found[0]
    v = {c: r[h[c]] for c in cols}
    hist = {m["Year"]: m for m in g["MajorHistorys"]}
    h25 = hist.get(2025, {})
    if str(g["PreMinMark"]) == str(v["26参考分"]):
        stats["PreMinMark==26参考分"] += 1
    if str(h25.get("EquivalentMinMark")) == str(v["同位分"]):
        stats["25等效==同位分"] += 1
    if str(g["MajorPlan"]) == str(v["26人"]):
        stats["plan==26人"] += 1
    if str(g["Tuition"]) == str(v["学费"]):
        stats["tuition==学费"] += 1
    if hit <= 6:
        print(g["UnvName"], g["MajorName"])
        print("  格学: plan", g["MajorPlan"], "PreMin", g["PreMinMark"], "PreAvg", g["PreAvgMark"],
              "| hist", [(y, m["MinMark"], m["MinMarkRank"], m["EquivalentMinMark"]) for y, m in sorted(hist.items())])
        print("  文件:", v)

print(f"\n匹配 {hit} 条, 未匹配 {miss} 条")
for k, n in stats.items():
    print(f"  {k}: {n}/{hit}")

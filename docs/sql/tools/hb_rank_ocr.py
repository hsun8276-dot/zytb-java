# -*- coding: utf-8 -*-
"""河北2026高考一分一段表 OCR解析入库(修正版)
来源: dxsbb 转载的河北省教育考试院《2026年河北省普通高校招生物理科目组合、历史科目组合考生成绩统计表》(6张扫描图)
表结构(视觉行实测): 分数档次 | 物理人数@x≈252 | 物理累计@x≈345 | 历史人数@x≈450 | 历史累计@x≈543
坑(本次踩到): ①物理累计列x会漂移(低分段~350, 中分段~344, 历史段也有物理列) 不能按单一x硬切
             ②稀疏表人数列常缺读(行内只有2-4个token) → 只信累计人数列, 同分人数=相邻累计差
             ③第一张图顶部物理/历史列标题行在y≈968(第2个表头), 顶部y≈147那行缺分组标题
"""
import json, os, glob
from rapidocr_onnxruntime import RapidOCR

IMG_DIR = r"F:\hbbxl\hb_rank"
CACHE = os.path.join(IMG_DIR, "ocr_cache")
X_SCORE = 107          # 分数档次列中心
X_PHYS_CUM = 347       # 物理累计列中心(容差±40)
X_HIST_CUM = 545       # 历史累计列中心(容差±40)
X_PHYS_CNT = 253       # 物理人数列中心
X_HIST_CNT = 450       # 历史人数列中心

def ocr_all():
    os.makedirs(CACHE, exist_ok=True)
    engine = RapidOCR()
    out = {}
    for f in sorted(glob.glob(os.path.join(IMG_DIR, "2026_*.jpg"))):
        key = os.path.basename(f)
        cache_f = os.path.join(CACHE, key + ".json")
        if os.path.exists(cache_f):
            out[key] = json.load(open(cache_f, encoding="utf-8"))
            continue
        res, _ = engine(f)
        lines = []
        for box, text, score in (res or []):
            xs = [p[0] for p in box]; ys = [p[1] for p in box]
            lines.append({"t": text.strip(), "x": sum(xs)/len(xs), "y": sum(ys)/len(ys)})
        json.dump(lines, open(cache_f, "w", encoding="utf-8"), ensure_ascii=False)
        out[key] = lines
        print("OCR", key, len(lines), "行")
    return out

def num(t):
    t = t.replace(",", "").replace(" ", "").replace("O", "0").replace("l", "1")
    return t.isdigit()

def parse_image(lines):
    rows = {}
    for l in lines:
        rows.setdefault(round(l["y"] / 9), []).append(l)
    parsed = []
    for k in sorted(rows):
        toks = sorted(rows[k], key=lambda z: z["x"])
        if not any("分数档次" in t["t"] for t in toks):
            pass  # 表头行也走下面逻辑, 靠数字过滤自然跳过
        score = None
        phys_cnt = phys_cum = hist_cnt = hist_cum = None
        for t in toks:
            txt = t["t"]
            x = t["x"]
            if num(txt):
                v = int(txt.replace(",", "").replace(" ", "").replace("O", "0").replace("l", "1"))
                if abs(x - X_SCORE) < 45 and 100 < v <= 750 and score is None:
                    score = v
                elif abs(x - X_PHYS_CUM) < 45 and phys_cum is None:
                    phys_cum = v
                elif abs(x - X_HIST_CUM) < 45 and hist_cum is None:
                    hist_cum = v
                elif abs(x - X_PHYS_CNT) < 30 and phys_cnt is None:
                    phys_cnt = v
                elif abs(x - X_HIST_CNT) < 28 and hist_cnt is None:
                    hist_cnt = v
            elif ("及以上" in txt or txt.endswith("分")) and score is None:
                m = __import__("re").search(r"(\d{3})", txt)
                if m and abs(x - X_SCORE) < 45:
                    score = int(m.group(1))
        if score is None or phys_cum is None:
            continue
        parsed.append({"score": score, "p_cnt": phys_cnt, "p_cum": phys_cum,
                       "h_cnt": hist_cnt, "h_cum": hist_cum})
    return parsed

def main():
    data = ocr_all()
    all_rows = []
    for key in sorted(data):
        got = parse_image(data[key])
        print(f"  {key}: 解析 {len(got)} 行")
        all_rows.extend(got)
    # 去重+排序
    seen, rows = set(), []
    for r in all_rows:
        if r["score"] in seen:
            continue
        seen.add(r["score"]); rows.append(r)
    rows.sort(key=lambda r: -r["score"])
    p_ok = all(rows[i]["p_cum"] >= rows[i+1]["p_cum"] for i in range(len(rows)-1))
    h_ok = all(rows[i]["h_cum"] >= rows[i+1]["h_cum"] for i in range(len(rows)-1) if rows[i]["h_cum"] and rows[i+1]["h_cum"])
    print(f"总分数行 {len(rows)} | 物理累计单调 {p_ok} | 历史累计单调 {h_ok}")
    print("最高:", rows[:2])
    print("最低:", rows[-2:])
    print("物理累计有值:", sum(1 for r in rows if r["p_cum"]), "| 历史累计有值:", sum(1 for r in rows if r["h_cum"]))
    json.dump(rows, open(IMG_DIR + "/hb2026_parsed.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("已存 hb_rank/hb2026_parsed.json")

if __name__ == "__main__":
    main()

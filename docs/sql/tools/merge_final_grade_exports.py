# -*- coding: utf-8 -*-
"""Merge final CDP grade/rank exports into gexue_major_grade_rank.json and sanitize DB."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "crawler"))

OUT = Path(__file__).parent / "data" / "gexue_major_grade_rank.json"
LOG_DIR = Path(r"C:\Users\EDY\.cursor\browser-logs")

# Final merged export files (fc0-fc10) + earlier physics if needed
FINAL_GLOBS = [
    "cdp-response-Runtime.evaluate-2026-10-08T16-29-59-874Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-02-824Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-03-696Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-04-201Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-08-375Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-10-263Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-10-653Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-10-604Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-27-986Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-30-28-220Z.json",
    "cdp-response-Runtime.evaluate-2026-10-08T16-31-04-366Z.json",
]


def extract_pairs(path: Path) -> dict:
    raw = json.loads(path.read_text(encoding="utf-8"))
    val = raw.get("result", {}).get("value", raw) if isinstance(raw, dict) else raw
    if isinstance(val, str):
        val = json.loads(val)
    merged = {}
    if not isinstance(val, dict):
        return merged
    # shapes: {total, c0:{}, c1:{}...} or {chunk:{}, ...} or direct id-> [g,r]
    parts = []
    for k, v in val.items():
        if isinstance(v, dict) and v and any(isinstance(x, (list, tuple, dict)) for x in list(v.values())[:3]):
            # check if looks like id map
            sample_k = next(iter(v))
            if isinstance(sample_k, str) and ("冀" in sample_k or sample_k.startswith("26")):
                parts.append(v)
            elif k.startswith("c") or k.startswith("fc") or k == "chunk":
                parts.append(v)
        elif k.startswith("c") or k.startswith("fc") or k == "chunk":
            if isinstance(v, dict):
                parts.append(v)
    if not parts and all(isinstance(v, (list, tuple, dict)) for v in list(val.values())[:5] if not isinstance(v, (int, float, str, bool, type(None)))):
        # maybe whole val is the map
        sample_k = next(iter(val))
        if isinstance(sample_k, str) and ("冀" in sample_k or sample_k.startswith("26")):
            parts.append(val)
    for part in parts:
        for rid, gr in part.items():
            if not isinstance(rid, str):
                continue
            if isinstance(gr, (list, tuple)) and len(gr) >= 2:
                g, r = str(gr[0]).strip(), int(gr[1])
            elif isinstance(gr, dict):
                g = str(gr.get("grade") or "").strip()
                r = int(gr.get("rank"))
            else:
                continue
            if g and r > 0:
                merged[rid] = {"grade": g, "rank": r}
    return merged


def main():
    all_map = {}
    if OUT.exists():
        all_map.update(json.loads(OUT.read_text(encoding="utf-8")))
        print("existing", len(all_map))
    for name in FINAL_GLOBS:
        p = LOG_DIR / name
        if not p.exists():
            print("missing", name)
            continue
        part = extract_pairs(p)
        print(f"{name}: +{len(part)}")
        all_map.update(part)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(all_map, ensure_ascii=False, indent=0), encoding="utf-8")
    print(f"merged total: {len(all_map)} -> {OUT}")


if __name__ == "__main__":
    main()

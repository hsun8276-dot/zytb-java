# -*- coding: utf-8 -*-
"""Merge scraped CDP grade maps into data/gexue_major_grade_rank.json then sanitize DB."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "crawler"))

OUT = Path(__file__).parent / "data" / "gexue_major_grade_rank.json"
LOGS = [
    Path(r"C:\Users\EDY\.cursor\browser-logs\cdp-response-Runtime.evaluate-2026-10-08T14-37-21-250Z.json"),
    Path(r"C:\Users\EDY\.cursor\browser-logs\cdp-response-Runtime.evaluate-2026-10-08T14-46-13-837Z.json"),
]


def extract(path: Path) -> dict:
    raw = json.loads(path.read_text(encoding="utf-8"))
    val = raw.get("result", {}).get("value", raw) if isinstance(raw, dict) else raw
    if isinstance(val, str):
        val = json.loads(val)
    merged = {}
    for k, part in val.items():
        if not k.startswith("c") or not isinstance(part, dict):
            continue
        for rid, gr in part.items():
            if isinstance(gr, (list, tuple)) and len(gr) >= 2:
                g, r = str(gr[0]).strip(), int(gr[1])
            elif isinstance(gr, dict):
                g, r = str(gr.get("grade") or "").strip(), int(gr.get("rank"))
            else:
                continue
            if g and r > 0:
                merged[rid] = {"grade": g, "rank": r}
    return merged


def main():
    all_map = {}
    if OUT.exists():
        all_map.update(json.loads(OUT.read_text(encoding="utf-8")))
    for p in LOGS:
        if not p.exists():
            print("missing", p)
            continue
        part = extract(p)
        print(f"{p.name}: +{len(part)}")
        # later scrape overwrites earlier (same source)
        all_map.update(part)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(all_map, ensure_ascii=False, indent=0), encoding="utf-8")
    print(f"merged total pairs: {len(all_map)} -> {OUT}")


if __name__ == "__main__":
    main()

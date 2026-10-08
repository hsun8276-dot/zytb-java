# -*- coding: utf-8 -*-
"""Generate JS payload to scrape GetUnvMajors grade/rank for all Hebei BK universities.
Run inside logged-in AspMng page via CDP (manual orchestration by agent).
This script only prepares the names JSON already exported.
"""
import json
from pathlib import Path

NAMES = Path(__file__).parent / "data" / "hebei_bk_unv_names.json"
OUT = Path(__file__).parent / "data" / "scrape_unv_batch_js.txt"

names = json.loads(NAMES.read_text(encoding="utf-8"))
# emit as JS array literal
js_names = json.dumps(names, ensure_ascii=False)
OUT.write_text(js_names, encoding="utf-8")
print(len(names), "names ->", OUT, "bytes", OUT.stat().st_size)

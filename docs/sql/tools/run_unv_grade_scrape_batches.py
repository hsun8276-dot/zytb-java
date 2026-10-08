# -*- coding: utf-8 -*-
"""Prepare sequential CDP batch expressions for GetUnvMajors grade scrape."""
import json
from pathlib import Path

ROOT = Path(__file__).parent / "data"
names = json.loads((ROOT / "hebei_bk_unv_names.json").read_text(encoding="utf-8"))
BATCH = 100
out_dir = ROOT / "cdp_batches"
out_dir.mkdir(exist_ok=True)

template = r'''(() => new Promise(async (resolve) => {
  const names = %NAMES%;
  const code = document.querySelector('#txtAspirationCode')?.value;
  const map = window.__gradeMapUnv || {};
  let ok=0, fail=0, added=0;
  for (let i=0; i<names.length; i++) {
    try {
      const r = await fetch('AspMng.aspx/GetUnvMajors', {
        method:'POST', credentials:'include',
        headers:{'Content-Type':'application/json; charset=utf-8'},
        body: JSON.stringify({AspirationCode:code, UnvName:names[i], MajorName:'', CityName:'', sPreMinMarkUnder:''})
      });
      const j = await r.json(); const d = j.d||j;
      if (!(d&&d.Success&&d.Data)) { fail++; continue; }
      const majors = typeof d.Data==='string'?JSON.parse(d.Data):d.Data;
      ok++;
      for (const m of majors) {
        const g=m.MajorGrade, rk=m.MajorRank;
        if (g!=null && String(g).trim()!=='' && rk!=null && rk!==0) {
          if (!map[m.RecruitMajorID]) added++;
          map[m.RecruitMajorID]=[String(g).trim(), rk];
        }
      }
    } catch(e) { fail++; }
  }
  window.__gradeMapUnv = map;
  window.__gradeKeysUnv = Object.keys(map).sort();
  resolve(JSON.stringify({batchStart:%START%, batch:names.length, ok, fail, pairs:window.__gradeKeysUnv.length, added, code}));
}))()'''

files = []
for i in range(0, len(names), BATCH):
    batch = names[i:i + BATCH]
    expr = template.replace("%NAMES%", json.dumps(batch, ensure_ascii=False)).replace("%START%", str(i))
    fp = out_dir / f"batch_{i:04d}.js"
    fp.write_text(expr, encoding="utf-8")
    files.append(str(fp))
(ROOT / "cdp_batch_list.json").write_text(json.dumps(files, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"{len(files)} batches x {BATCH}, total names {len(names)}")

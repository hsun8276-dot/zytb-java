import sys
import glob
import openpyxl

out = open(sys.argv[2], "w", encoding="utf-8-sig", newline="\n")

for path in sorted(glob.glob(sys.argv[1] + "/*.xlsx")):
    print("=" * 100, file=out)
    print(path, file=out)
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    for ws in wb.worksheets:
        rows = list(ws.iter_rows(values_only=True))
        header, data = rows[0], rows[1:]
        print(f"--- sheet={ws.title} rows={len(data)} cols={len(header)}", file=out)
        for i, h in enumerate(header):
            col = [r[i] for r in data]
            filled = [v for v in col if v not in (None, "", 0)]
            samples = list(dict.fromkeys(str(v) for v in filled))[:4]
            print(f"  [{i:2}] {h!s:<12} 非空={len(filled):>6}  样例={samples}", file=out)
        if "招生类型" in header:
            idx = header.index("招生类型")
            kinds = {}
            for r in data:
                kinds[r[idx]] = kinds.get(r[idx], 0) + 1
            print(f"  招生类型分布: {kinds}", file=out)
out.close()

# -*- coding: utf-8 -*-
"""临时查询: python db_peek.py "SQL1" "SQL2" ...(写语句自动提交)"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'crawler'))
from common import db

conn = db()
c = conn.cursor()
for q in sys.argv[1:]:
    n = c.execute(q)
    print('>>', q)
    if c.description is None:
        print('   affected', n)
    for r in c.fetchall():
        print('  ', r)
conn.commit()

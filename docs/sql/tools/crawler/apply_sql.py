# -*- coding: utf-8 -*-
"""执行 docs/sql/rebuild/*.sql 到 zytb(幂等: 重复列/重复索引/表已存在 直接跳过)
用法: python apply_sql.py 04_crawl.sql
"""
import os, sys
import pymysql
from common import db, REPO

IGNORE_ERRNO = {1060, 1061, 1050}  # Duplicate column / Duplicate key name / Table exists


def main():
    for name in sys.argv[1:]:
        path = name if os.path.isabs(name) else os.path.join(REPO, 'docs', 'sql', 'rebuild', name)
        sql = open(path, encoding='utf-8').read()
        conn = db(); cur = conn.cursor()
        n_ok = n_skip = 0
        for stmt in sql.split(';'):
            s = '\n'.join(l for l in stmt.splitlines() if not l.strip().startswith('--')).strip()
            if not s or s.upper().startswith('SET NAMES'):
                continue
            try:
                cur.execute(s); n_ok += 1
            except pymysql.err.MySQLError as e:
                if e.args[0] in IGNORE_ERRNO:
                    n_skip += 1
                else:
                    raise
        conn.commit(); conn.close()
        print(f'{os.path.basename(path)}: 执行 {n_ok} 条, 跳过(已存在) {n_skip} 条')


if __name__ == '__main__':
    main()

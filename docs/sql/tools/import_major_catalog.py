# -*- coding: utf-8 -*-
"""从格学 majors API 拉取普本/职本/专科目录写入 zytb.major_catalog（仅综合查询用，不影响填报 major 表）"""
from __future__ import annotations

import json
import time
import urllib.request

import pymysql

BASE = "http://39.107.86.41:9003/majors"
DB = dict(host="127.0.0.1", user="root", password="root", database="zytb", charset="utf8mb4")

LEVELS = (1, 2, 3)


def get_json(url: str):
    with urllib.request.urlopen(url, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def ensure_table(cur):
    cur.execute(
        """
        CREATE TABLE IF NOT EXISTS major_catalog (
            id               BIGINT AUTO_INCREMENT PRIMARY KEY,
            edu_level        TINYINT      NOT NULL COMMENT '1普本 2职本 3专科',
            major_code       VARCHAR(20)  NOT NULL COMMENT '专业国标代码',
            major_name       VARCHAR(100) NOT NULL COMMENT '专业名称',
            category         VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '学科门类/大类',
            first_discipline VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '专业类',
            group_code       VARCHAR(10)  NOT NULL DEFAULT '' COMMENT '专业类国标码',
            create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            UNIQUE KEY uk_level_code (edu_level, major_code),
            KEY idx_level_group (edu_level, group_code),
            KEY idx_level_cat (edu_level, category)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='综合查询·专业目录(普本/职本/专科)'
        """
    )


def main():
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    ensure_table(cur)
    cur.execute("DELETE FROM major_catalog")
    total = 0

    for level in LEVELS:
        group_resp = get_json(f"{BASE}/getMajorGroup?level={level}")
        data = group_resp.get("data") or {}
        # preserve API category key order
        groups = []
        for cat, items in data.items():
            for it in items:
                groups.append(
                    (
                        level,
                        it.get("majorCategoryCode") or "",
                        it.get("majorCategory") or "",
                        it.get("categories") or cat or "",
                    )
                )
        print(f"level={level} groups={len(groups)}")
        for level_i, group_code, disc, cat in groups:
            if not group_code:
                continue
            maj = get_json(f"{BASE}/getMajors?majorGroup={group_code}")
            payload = (maj.get("data") or {})
            majors = payload.get("majors") or []
            # prefer payload names if present
            disc_name = payload.get("majorGroup") or disc
            gcode = payload.get("majorGroupCode") or group_code
            for m in majors:
                code = (m.get("majorCode") or "").strip()
                name = (m.get("majors") or m.get("majorName") or "").strip()
                if not code or not name:
                    continue
                cur.execute(
                    """
                    INSERT INTO major_catalog
                      (edu_level, major_code, major_name, category, first_discipline, group_code)
                    VALUES (%s,%s,%s,%s,%s,%s)
                    ON DUPLICATE KEY UPDATE
                      major_name=VALUES(major_name),
                      category=VALUES(category),
                      first_discipline=VALUES(first_discipline),
                      group_code=VALUES(group_code)
                    """,
                    (level_i, code, name, cat, disc_name, gcode),
                )
                total += 1
            time.sleep(0.05)
        conn.commit()

    cur.execute("SELECT edu_level, COUNT(*) FROM major_catalog GROUP BY edu_level ORDER BY edu_level")
    print("counts:", cur.fetchall())
    print("inserted/updated rows approx:", total)
    conn.close()


if __name__ == "__main__":
    main()

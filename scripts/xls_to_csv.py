#!/usr/bin/env python3
"""将 Excel 工作簿（.xls）中的每个工作表导出为独立的 CSV 文件。"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

import xlrd

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_XLS = PROJECT_ROOT / "data" / "raw" / "farm_data.xls"
DEFAULT_OUTPUT = PROJECT_ROOT / "data" / "csv"

# 工作表中文名 -> 英文 CSV 文件名
SHEET_NAME_MAP: dict[str, str] = {
    "农作物表": "crops",
    "培育度表": "cultivation",
    "小摊表": "stall",
    "土地表": "land",
    "农场等级表": "farm_levels",
    "奖励表": "rewards",
    "变异倍率表": "mutation_rates",
}

# 每个工作表：中文列名 -> 英文字段名
# 培育度表的"备注"列不导出（设为 None）
HEADER_MAP: dict[str, dict[str, str | None]] = {
    "农场等级表": {
        "等级": "level",
        "升级费用": "upgrade_cost",
        "需要经验": "required_exp",
        "解锁内容": "unlock_content",
    },
    "小摊表": {
        "升级后等级": "level",
        "升级费用": "upgrade_cost",
        "获得经验": "gain_exp",
        "需要等级": "required_farm_level",
        "提升售价": "price_boost",
    },
    "土地表": {
        "农田": "land_index",
        "开垦费用": "reclaim_cost",
        "获得经验": "gain_exp",
        "需要等级": "required_level",
    },
    "农作物表": {
        "等级": "unlock_level",
        "作物": "name",
        "购买价格·": "seed_price",
        "产量": "yield_qty",
        "总售价": "total_sell_price",
        "经验": "exp_gain",
        "收获时间": "harvest_time",
        "变异上限": "mutation_limit",
        "变异英雄": "mutation_hero",
    },
    "培育度表": {
        "作物": "crop",
        "合计": "total",
        "2级": "lv2",
        "3级": "lv3",
        "4级": "lv4",
        "5级": "lv5",
        "6级": "lv6",
        "7级": "lv7",
        "8级": "lv8",
        "9级": "lv9",
        "10级": "lv10",
        "备注(该列为空的作物，等级在1-8级时，每升1级售价额外提升10%，9级升10级额外提升20%)": None,  # 不导出
    },
    "变异倍率表": {
        "变异类型": "mutation_type",
        "品质": "quality",
        "倍率": "multiplier",
        "解锁等级": "unlock_level",
    },
    "奖励表": {
        "等级": "level",
        "金币奖励": "gold_reward",
        "经验奖励": "exp_reward",
    },
}


def sanitize_filename(name: str) -> str:
    """移除 Windows 文件名非法字符。"""
    cleaned = re.sub(r'[<>:"/\\|?*]', "_", name.strip())
    return cleaned or "sheet"


def sheet_csv_name(sheet_name: str) -> str:
    mapped = SHEET_NAME_MAP.get(sheet_name.strip())
    if mapped:
        return mapped
    return sanitize_filename(sheet_name)


def _format_number(value: float, fmt_str: str) -> str:
    if "%" in fmt_str:
        pct = value * 100
        match = re.search(r"0(\.0+)%", fmt_str)
        if match:
            decimals = len(match.group(1)) - 1
            text = f"{pct:.{decimals}f}"
            if "." in text:
                text = text.rstrip("0").rstrip(".")
            return f"{text}%"
        return f"{round(pct)}%"

    if value == int(value):
        return str(int(value))

    text = format(value, ".15g")
    if "e" in text or "E" in text:
        return format(value, "f").rstrip("0").rstrip(".")
    return text


def cell_to_str(book: xlrd.Book, cell: xlrd.sheet.Cell) -> str:
    if cell.ctype in (xlrd.XL_CELL_EMPTY, xlrd.XL_CELL_BLANK):
        return ""
    if cell.ctype == xlrd.XL_CELL_TEXT:
        return cell.value
    if cell.ctype == xlrd.XL_CELL_BOOLEAN:
        return "TRUE" if cell.value else "FALSE"
    if cell.ctype == xlrd.XL_CELL_ERROR:
        return xlrd.error_text_from_code.get(cell.value, "")

    if cell.ctype == xlrd.XL_CELL_NUMBER:
        xf = book.xf_list[cell.xf_index]
        fmt = book.format_map.get(xf.format_key)
        fmt_str = fmt.format_str if fmt else "General"
        return _format_number(cell.value, fmt_str)

    if cell.ctype == xlrd.XL_CELL_DATE:
        return str(cell.value)

    return str(cell.value)


def convert_xls_to_csv(
    xls_path: Path,
    output_dir: Path,
    encoding: str = "utf-8-sig",
) -> list[Path]:
    """
    将 xls 文件中的所有工作表转为 CSV。
    表头中文列名会映射为英文字段名（见 HEADER_MAP）。
    培育度表的"备注"列不导出。

    返回已写入的 CSV 文件路径列表。
    """
    if not xls_path.is_file():
        raise FileNotFoundError(f"找不到文件: {xls_path}")

    output_dir.mkdir(parents=True, exist_ok=True)

    book = xlrd.open_workbook(str(xls_path), formatting_info=True)
    written: list[Path] = []

    for sheet_name in book.sheet_names():
        sheet = book.sheet_by_name(sheet_name)
        csv_path = output_dir / f"{sheet_csv_name(sheet_name)}.csv"
        header_map = HEADER_MAP.get(sheet_name.strip())  # type: ignore[union-attr]

        with csv_path.open("w", newline="", encoding=encoding) as fp:
            writer = csv.writer(fp)

            # 第一行：表头（可能过滤掉 header_map 中值为 None 的列）
            if sheet.nrows > 0:
                raw_header = [
                    sheet.cell_value(0, col_idx) for col_idx in range(sheet.ncols)
                ]
                if header_map:
                    # 用英文替换，同时过滤掉 mapped 为 None 的列（记录被过滤的列索引）
                    filtered = [
                        (col_idx, header_map.get(str(cell), str(cell)))
                        for col_idx, cell in enumerate(raw_header)
                        if header_map.get(str(cell)) is not None
                    ]
                    header_col_indices = [col_idx for col_idx, _ in filtered]
                    header_values = [eng for _, eng in filtered]
                else:
                    header_col_indices = list(range(sheet.ncols))
                    header_values = [str(cell) for cell in raw_header]
                writer.writerow(header_values)
            else:
                header_col_indices = list(range(sheet.ncols))
                header_values = []

            # 数据行：只写未被过滤的列
            for row_idx in range(1, sheet.nrows):
                writer.writerow(
                    cell_to_str(book, sheet.cell(row_idx, col_idx))
                    for col_idx in header_col_indices
                )

        written.append(csv_path)
        print(f"  {sheet_name!r} -> {csv_path.name}")

    return written


def main() -> int:
    parser = argparse.ArgumentParser(description="将 .xls 文件的所有工作表转为 CSV")
    parser.add_argument(
        "xls_file",
        nargs="?",
        default=str(DEFAULT_XLS),
        help=f"输入的 .xls 文件路径（默认: {DEFAULT_XLS.relative_to(PROJECT_ROOT)}）",
    )
    parser.add_argument(
        "-o",
        "--output",
        default=str(DEFAULT_OUTPUT),
        help=f"输出目录（默认: {DEFAULT_OUTPUT.relative_to(PROJECT_ROOT)}）",
    )
    args = parser.parse_args()

    xls_path = Path(args.xls_file).resolve()
    output_dir = Path(args.output).resolve()

    print(f"读取: {xls_path}")
    print(f"输出: {output_dir}")
    try:
        written = convert_xls_to_csv(xls_path, output_dir)
    except FileNotFoundError as exc:
        print(f"错误: {exc}", file=sys.stderr)
        return 1
    except ImportError:
        print("错误: 读取 .xls 需要 xlrd，请运行: pip install -r requirements.txt", file=sys.stderr)
        return 1

    print(f"\n完成，共导出 {len(written)} 个 CSV 文件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

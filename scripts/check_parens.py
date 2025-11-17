#!/usr/bin/env python3
"""
Balance-check Clojure parentheses/brackets/braces and attribute issues to forms.
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence, Tuple


OPENING = {"(": ")", "[": "]", "{": "}"}
CLOSING = {v: k for k, v in OPENING.items()}
SIGNIFICANT_EXTENSIONS = {".clj", ".cljc", ".cljs"}
WHITESPACE = set(" \t\r\n,")
DEF_LIKE = {
    "def",
    "defonce",
    "defn",
    "defmacro",
    "defmethod",
    "defmulti",
    "defrecord",
    "defprotocol",
    "deftype",
    "definterface",
}


@dataclass
class ParenEntry:
    char: str
    line: int
    col: int
    form: str


def gather_files(paths: Sequence[str]) -> Tuple[List[Path], List[str]]:
    files: List[Path] = []
    missing: List[str] = []
    for raw in paths:
        p = Path(raw)
        if not p.exists():
            missing.append(raw)
            continue
        if p.is_dir():
            for ext in SIGNIFICANT_EXTENSIONS:
                for match in sorted(p.rglob(f"*{ext}")):
                    if match.is_file():
                        files.append(match)
        else:
            files.append(p)
    return files, missing


def skip_comment(text: str, idx: int) -> int:
    while idx < len(text) and text[idx] != "\n":
        idx += 1
    return idx


def skip_space_and_comments(text: str, idx: int) -> int:
    length = len(text)
    while idx < length:
        ch = text[idx]
        if ch in WHITESPACE:
            idx += 1
            continue
        if ch == ";":
            idx = skip_comment(text, idx)
            continue
        break
    return idx


def read_symbol(text: str, idx: int) -> Tuple[Optional[str], int]:
    idx = skip_space_and_comments(text, idx)
    length = len(text)
    if idx >= length:
        return None, idx
    ch = text[idx]
    if ch in '()[]{}"':
        return None, idx
    start = idx
    while idx < length:
        ch = text[idx]
        if ch in WHITESPACE or ch in '()[]{}"':
            break
        if ch == ";":
            break
        idx += 1
    return text[start:idx], idx


def describe_top_level_form(text: str, idx: int, line: int) -> str:
    symbol, after_symbol = read_symbol(text, idx)
    if not symbol:
        return f"form starting line {line}"
    if symbol in DEF_LIKE or symbol == "ns":
        name, _ = read_symbol(text, after_symbol)
        if name:
            return f"{symbol} {name}"
    return f"{symbol} form line {line}"


def check_file(path: Path) -> List[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return [f"{path}: unable to read file ({exc})"]

    stack: List[ParenEntry] = []
    errors: List[str] = []
    line = 1
    col = 0
    idx = 0
    current_form = "top-level"
    in_string = False
    escape = False
    in_comment = False

    while idx < len(text):
        ch = text[idx]
        if ch == "\n":
            line += 1
            col = 0
            in_comment = False
            idx += 1
            continue

        col += 1

        if in_comment:
            idx += 1
            continue

        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            idx += 1
            continue

        if ch == ";":
            in_comment = True
            idx += 1
            continue

        if ch == '"':
            in_string = True
            idx += 1
            continue

        if ch in OPENING:
            if ch == "(" and not stack:
                current_form = describe_top_level_form(text, idx + 1, line)
            stack.append(ParenEntry(ch, line, col, current_form))
            idx += 1
            continue

        if ch in CLOSING:
            if not stack:
                errors.append(
                    f"{path}:{line}:{col} unexpected '{ch}' (outside any open form)"
                )
                idx += 1
                continue

            entry = stack.pop()
            expected_open = CLOSING[ch]
            if entry.char != expected_open:
                errors.append(
                    f"{path}:{line}:{col} closing '{ch}' expected to match '{expected_open}' but last open was '{entry.char}' from {entry.form} (line {entry.line}, col {entry.col})"
                )
            idx += 1
            if not stack:
                current_form = "top-level"
            continue

        idx += 1

    if stack:
        for entry in reversed(stack):
            errors.append(
                f"{path}:{entry.line}:{entry.col} open '{entry.char}' was never closed (inside {entry.form})"
            )

    return errors


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Detect unbalanced parentheses/brackets/braces in Clojure files and "
            "report the enclosing form."
        )
    )
    parser.add_argument(
        "paths",
        nargs="+",
        help="Clojure files or directories (directories are scanned recursively).",
    )
    args = parser.parse_args(argv)
    files, missing = gather_files(args.paths)
    exit_code = 0

    if missing:
        for name in missing:
            print(f"{name}: no such file or directory", file=sys.stderr)
        exit_code = 1

    if not files:
        return exit_code or 1

    for file_path in files:
        problems = check_file(file_path)
        if problems:
            exit_code = 1
            print(f"{file_path}:")
            for issue in problems:
                print(f"  - {issue}")
        else:
            print(f"{file_path}: balanced")

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())

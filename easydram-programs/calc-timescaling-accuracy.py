#!/usr/bin/env python3
import argparse
import re
import sys
from pathlib import Path


BASE_CFG = "EasyDRAMRocketConfig"
VERIFY_CFG = "EasyDRAMRocketVerifyConfig"

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_OUTPUT_DIR = SCRIPT_DIR / "verify"

PROGRAM_LIST = [
    "gramschmidt", "trisolv", "cholesky",
    "ludcmp", "lu", "durbin",
    "symm", "gemm", "syr2k",
    "syrk", "gesummv", "gemver",
    "trmm", "atax", "mvt",
    "3mm", "doitgen", "bicg",
    "2mm", "floyd-warshall", "nussinov",
    "deriche", "covariance", "correlation",
    "seidel-2d", "heat-3d", "adi",
    "jacobi-1d", "jacobi-2d", "fdtd-2d",
]

CYCLE_RE = re.compile(r"\bcycles:(\d+)\b")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Compare EasyDRAM time-scaled and RTL-reference simulation cycle counts."
    )
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--base-config", default=BASE_CFG)
    parser.add_argument("--verify-config", default=VERIFY_CFG)
    parser.add_argument("--program", action="append",
                        help="Program name to compare. May be passed multiple times.")
    parser.add_argument("--all-polybench", action="store_true",
                        help="Compare the built-in PolyBench validation list.")
    return parser.parse_args()


def get_cycles(log_path):
    cycles = None
    with log_path.open("r", encoding="utf-8") as log:
        for line in log:
            if "!#POLYRES#!" not in line:
                continue
            match = CYCLE_RE.search(line)
            if match:
                cycles = int(match.group(1))
    return cycles


def discover_programs(output_dir, base_config, verify_config):
    suffix = f"-{base_config}.out"
    programs = []
    for base_log in sorted(output_dir.glob(f"*{suffix}")):
        program = base_log.name[:-len(suffix)]
        verify_log = output_dir / f"{program}-{verify_config}.out"
        if verify_log.exists():
            programs.append(program)
    return programs


def selected_programs(args):
    programs = []
    if args.all_polybench:
        programs.extend(PROGRAM_LIST)
    if args.program:
        programs.extend(args.program)
    if programs:
        return programs
    return discover_programs(args.output_dir, args.base_config, args.verify_config)


def main():
    args = parse_args()
    programs = selected_programs(args)
    if not programs:
        print(f"No matching log pairs found in {args.output_dir}", file=sys.stderr)
        return 1

    total_error = 0.0
    total_count = 0
    max_error = 0.0
    missing = 0

    for program in programs:
        base_log = args.output_dir / f"{program}-{args.base_config}.out"
        verify_log = args.output_dir / f"{program}-{args.verify_config}.out"
        if not base_log.exists() or not verify_log.exists():
            print(f"{program}: missing log pair", file=sys.stderr)
            missing += 1
            continue

        base_cycle = get_cycles(base_log)
        verify_cycle = get_cycles(verify_log)
        if base_cycle is None or verify_cycle is None:
            print(f"{program}: missing !#POLYRES#! cycles marker", file=sys.stderr)
            missing += 1
            continue

        error = abs((verify_cycle - base_cycle) / verify_cycle)
        print(f"{program}: base={base_cycle} verify={verify_cycle} error={error * 100:.4f}%")
        max_error = max(max_error, error)
        total_error += error
        total_count += 1

    if total_count == 0:
        return 1

    print(
        f"Across {total_count} sims. "
        f"Avg. Err (%): {total_error / total_count * 100:.2f} "
        f"Max. Err (%): {max_error * 100:.2f}"
    )
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())

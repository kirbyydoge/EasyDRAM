#!/usr/bin/env python3
import argparse
import shlex
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


BASE_CFG = "EasyDRAMRocketConfig"
VERIFY_CFG = "EasyDRAMRocketVerifyConfig"

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_CHIPYARD_DIR = REPO_ROOT / "easydram-chipyard"
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


def parse_args():
    parser = argparse.ArgumentParser(
        description="Run the EasyDRAM time-scaling validation on one or more RISC-V binaries."
    )
    parser.add_argument("--chipyard-dir", type=Path, default=DEFAULT_CHIPYARD_DIR)
    parser.add_argument("--sim-dir", type=Path, default=None,
                        help="Verilator directory. Defaults to <chipyard-dir>/sims/verilator.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--base-config", default=BASE_CFG)
    parser.add_argument("--verify-config", default=VERIFY_CFG)
    parser.add_argument("--max-cycles", default="10000000000")
    parser.add_argument("--threads", type=int, default=1)
    parser.add_argument("--build", action="store_true",
                        help="Build both Verilator simulators before running binaries.")
    parser.add_argument("--clean-build", action="store_true",
                        help="Run make clean for each simulator config before building.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print build and simulation commands without executing them.")
    parser.add_argument("--extra-sim-arg", action="append", default=[],
                        help="Extra simulator argument. May be passed multiple times.")

    binary_group = parser.add_argument_group("binary selection")
    binary_group.add_argument("--binary", action="append", type=Path,
                              help="Path to a RISC-V ELF/binary. May be passed multiple times.")
    binary_group.add_argument("--program", action="append",
                              help="Program name under --program-dir, without .riscv.")
    binary_group.add_argument("--program-dir", type=Path,
                              help="Directory containing <program>.riscv binaries.")
    binary_group.add_argument("--all-polybench", action="store_true",
                              help="Run the built-in PolyBench validation list from --program-dir.")
    return parser.parse_args()


def simulator_path(sim_dir, config):
    return sim_dir / f"simulator-chipyard.harness-{config}"


def shell_join(cmd):
    return " ".join(shlex.quote(str(arg)) for arg in cmd)


def run_or_print(cmd, cwd=None, dry_run=False):
    if dry_run:
        prefix = f"(cd {cwd} && " if cwd else ""
        suffix = ")" if cwd else ""
        print(prefix + shell_join(cmd) + suffix)
        return 0
    return subprocess.run(cmd, cwd=cwd).returncode


def build_simulators(sim_dir, configs, clean_build, dry_run):
    for config in configs:
        if clean_build:
            rc = run_or_print(["make", f"CONFIG={config}", "clean"], cwd=sim_dir, dry_run=dry_run)
            if rc != 0:
                return rc
        rc = run_or_print(["make", f"CONFIG={config}"], cwd=sim_dir, dry_run=dry_run)
        if rc != 0:
            return rc
    return 0


def selected_binaries(args):
    binaries = []
    for binary in args.binary or []:
        binaries.append((binary.stem, binary))

    programs = []
    if args.all_polybench:
        programs.extend(PROGRAM_LIST)
    if args.program:
        programs.extend(args.program)

    if programs and args.program_dir is None:
        raise SystemExit("--program/--all-polybench requires --program-dir")

    for program in programs:
        binaries.append((program, args.program_dir / f"{program}.riscv"))

    if not binaries:
        raise SystemExit("Select at least one workload with --binary, --program, or --all-polybench.")
    return binaries


def sim_command(sim_dir, config, binary, max_cycles, extra_args):
    return [
        simulator_path(sim_dir, config),
        "+permissive",
        f"+max_cycles={max_cycles}",
        "+permissive-off",
        *extra_args,
        binary,
    ]


def run_simulation(name, config, binary, sim_dir, output_dir, max_cycles, extra_args, dry_run):
    log_path = output_dir / f"{name}-{config}.out"
    cmd = sim_command(sim_dir, config, binary, max_cycles, extra_args)

    if dry_run:
        print(f"{shell_join(cmd)} > {shlex.quote(str(log_path))} 2>&1")
        return 0

    start = time.monotonic()
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"$ {shell_join(cmd)}\n")
        log.flush()
        rc = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT).returncode
        elapsed = time.monotonic() - start
        log.write(f"\n[verify-timescaling] returncode={rc} elapsed_seconds={elapsed:.2f}\n")
    return rc


def validate_inputs(binaries, sim_dir, configs, build, dry_run):
    if dry_run:
        return
    if not sim_dir.exists():
        raise SystemExit(f"Verilator directory does not exist: {sim_dir}")
    for _, binary in binaries:
        if not binary.exists():
            raise SystemExit(f"Binary does not exist: {binary}")
    if not build:
        missing = [simulator_path(sim_dir, config) for config in configs
                   if not simulator_path(sim_dir, config).exists()]
        if missing:
            formatted = "\n".join(str(path) for path in missing)
            raise SystemExit(f"Simulator missing; rerun with --build or build manually:\n{formatted}")


def main():
    args = parse_args()
    sim_dir = args.sim_dir or (args.chipyard_dir / "sims" / "verilator")
    configs = [args.base_config, args.verify_config]
    binaries = selected_binaries(args)

    validate_inputs(binaries, sim_dir, configs, args.build, args.dry_run)
    if not args.dry_run:
        args.output_dir.mkdir(parents=True, exist_ok=True)

    if args.build:
        rc = build_simulators(sim_dir, configs, args.clean_build, args.dry_run)
        if rc != 0:
            return rc

    jobs = []
    with ThreadPoolExecutor(max_workers=args.threads) as executor:
        for name, binary in binaries:
            for config in configs:
                jobs.append(executor.submit(
                    run_simulation,
                    name,
                    config,
                    binary,
                    sim_dir,
                    args.output_dir,
                    args.max_cycles,
                    args.extra_sim_arg,
                    args.dry_run,
                ))

        failed = 0
        for job in as_completed(jobs):
            failed += 1 if job.result() != 0 else 0
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())

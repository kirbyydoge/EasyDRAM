# EasyDRAM Programs

## Building PolyBench Workloads

From the repository root, use the RISC-V toolchain from this repository's
Chipyard environment:

```bash
cd easydram-chipyard
source env.sh

cd ../easydram-programs/tests/PolyBenchC-4.2.1
./makeall.sh
```

The compiled binaries and dumps are written to:

```text
easydram-programs/install/riscv-bmarks
```

To write them somewhere else, override `BENCHMARK_DIR`:

```bash
BENCHMARK_DIR=/path/to/riscv-bmarks ./makeall.sh
```

## Time Scaling Validation

Section 6 of the EasyDRAM paper validates time scaling by running the same
binary on two RTL systems:

- `EasyDRAMRocketConfig`: the EasyDRAM system with a 100 MHz processor clock
  using time scaling to model a 1 GHz target.
- `EasyDRAMRocketVerifyConfig`: the 1 GHz RTL reference system without time
  scaling.

From the repository root, build and run both simulators for a single RISC-V
binary with:

```bash
cd easydram-chipyard
source env.sh

cd ../easydram-programs
./verify-timescaling.py \
  --build --clean-build \
  --binary /path/to/workload.riscv
```

To reuse already-built simulators, omit `--build --clean-build`:

```bash
./verify-timescaling.py --binary /path/to/workload.riscv
```

The script writes one log per system:

```text
verify/workload-EasyDRAMRocketConfig.out
verify/workload-EasyDRAMRocketVerifyConfig.out
```

For PolyBench-style workloads that print a line containing
`!#POLYRES#! cycle:<count>`, compare the two runs with:

```bash
./calc-timescaling-accuracy.py --program workload
```

To run the original PolyBench validation list, first point the script at a
directory containing `<benchmark>.riscv` files:

```bash
./verify-timescaling.py \
  --build --clean-build \
  --program-dir install/riscv-bmarks \
  --all-polybench

./calc-timescaling-accuracy.py --all-polybench
```

Use `--dry-run` to print the exact build and simulation commands without
executing them.

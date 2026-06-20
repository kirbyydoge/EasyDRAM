## Repository File Structure

```
.
+-- ae_results/                     # Simulation results (created when experiments are executed)
+-- cputraces/                      # Generated EasyDRAM RowClone CPU traces
+-- mixes/                          # Workload mixes
|   +-- rowclone.mix                # EasyDRAM RowClone workloads
+-- scripts/                        # Scripts to generate runs and post-process raw data
+-- src/                            # Ramulator2 source code
|   +-- dram/impl/DDR5-VRR.cpp      # DDR5 model extended with EasyDRAM RowClone commands
|   +-- frontend/impl/processor/depO3/
|   |   +-- depcore.cpp             # Dependent-O3 frontend with EasyDRAM RowClone trace support
|   ...
...
+-- base_config.yaml                # Base Ramulator2 configuration for the simple test
+-- gen_rowclone_traces.py          # Generates EasyDRAM RowClone traces
+-- parse_results.py                # Extracts EasyDRAM RowClone statistics into dump.csv
+-- run_simple_test.sh              # Builds Ramulator2 and runs a simple RowClone test
+-- run_with_personalcomputer.sh    # Generates and runs the EasyDRAM RowClone experiment batch locally
+-- README.md                       # This file
```

## Installation Guide:

### Prerequisites:
- Git
- g++ with c++20 capabilities (g++-10 or above recommended)
- Python3 (3.10 or above recommended)

### Installation steps:
1. Clone the repository `git clone https://github.com/CMU-SAFARI/EasyDRAM.git`
2. Go to the Ramulator directory with `cd EasyDRAM/easydram-ramulator`
3. Install python dependencies, build Ramulator2, generate EasyDRAM RowClone traces, and run a simple test with `./run_simple_test.sh`

## Example Use
1. Run Ramulator2 simulations with `./run_with_personalcomputer.sh`
2. Wait for the simulations to finish. You can use `./check_run_status.sh` to track simulation progress for multicore and singlecore runs (this script also creates intermediate scripts that can restart failed runs)
3. Parse simulation results and collect EasyDRAM RowClone statistics with `python3 ./parse_results.py`
4. The parsed results are written to `dump.csv`

## Simulation Configuration Parameters
Execution of Ramulator2 simulations can be configured with the following configuration parameters. These parameters reside in `scripts/run_config.py` unless the parameter description below states a different path.

`PERSONAL_RUN_THREADS`: Number of parallel threads used to launch simulations with `./run_with_personalcomputer.sh`

`NUM_EXPECTED_INSTS`: Number of instructions the slowest core must execute before the simulation ends

`NUM_MAX_CYCLES`: Maximum number of cycles the simulation is allowed to run

`CONTROLLER`: Ramulator2 memory controller implementation used by generated configurations

`SCHEDULER`: Ramulator2 scheduler implementation used by generated configurations

Trace generation parameters, including the tested data sizes, reside in `easy_trace_cfg.py`.

## Contacts:
Oğuzhan Canpolat (aqwoguz [at] gmail [dot] com)

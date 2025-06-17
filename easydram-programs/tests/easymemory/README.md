# EasyMemory Test Runner

This test setup allows you to run a benchmark with EasyDRAM. The script programs the FPGA with the specified EasyDRAM configuration, loads a software memory controller binary, and then loads and runs a benchmark, saving the output to `out.txt`.

## Requirements

- Vivado installed and available in your `PATH` (any version compatible with Chipyard's FPGA flow)
- Python 3 with `pyserial` installed
- Root permissions to access serial devices
- `elf2hex` and preferred benchmarks (e.g., RISC-V and PolyBench) precompiled at the `BENCHMARK_PATH` set within the script

## Script Location

The main script is located at:

```
easydram-programs/tests/easymemory/send_benchmark.sh
```

## Usage

```bash
./send_benchmark.sh <uart_port> <memory_controller_folder> <benchmark_name> <config>
```

### Arguments

- `<uart_port>`: UART port to which your FPGA is connected (e.g., `/dev/ttyUSB0`)
- `<memory_controller_folder>`: Directory containing the compiled memory controller hex file
- `<benchmark_name>`: Name of the benchmark (without extension, e.g., `trisolv`)
- `<config>`: EasyDRAM Chipyard configuration to use (e.g., `EasyDRAMRocketConfig`)

### Example

```bash
./send_benchmark.sh /dev/ttyUSB0 frfcfs_mc trisolv EasyDRAMRocketConfig
```

## What It Does

1. Cleans and rebuilds the memory controller.
2. Converts the benchmark ELF to HEX using `elf2hex`.
3. Programs the FPGA with the EasyDRAM bitstream using Vivado.
4. Sends the memory controller HEX to the FPGA using `send_alone.py`.
5. Sends the benchmark HEX and listens for output using `send_listen.py`, saving the output to `out.txt`.

> Make sure that all environment variables and paths inside `send_benchmark.sh` are correctly set, especially:
> - `CHIPYARD_BASE`
> - `BENCHMARK_PATH`

**EasyDRAM: An FPGA-based Infrastructure for Fast and Accurate End-to-End Evaluation of Emerging DRAM Techniques**  
_DSN 2025_

EasyDRAM is an open-source infrastructure that enables fast and accurate end-to-end evaluation of DRAM techniques on FPGAs. This repository contains the full source code for EasyDRAM, including hardware modules, simulation infrastructure, and benchmarks.

An extended version of our conference paper is available at https://arxiv.org/abs/2506.10441.

## Repository Structure

- **`prebuilt/`**  
  Contains ready-made EasyDRAM system bitstreams for the AMD/Xilinx VCU108 board. To generate these bitstreams or bitstreams for other designs, see Section "Generating an EasyDRAM Bitstream".

- **`easydram-chipyard/`**  
  A modified version of [Chipyard](https://chipyard.readthedocs.io/en/latest/) that includes EasyDRAM integration.
  - **`generators/easydram/`**  
    This directory contains the main RTL implementation of EasyDRAM.

- **`easydram-ramulator/`**  
  Contains our Ramulator-based evaluation infrastructure for DRAM techniques.  
  This version includes our evaluation of **RowClone** within Ramulator 2.0.  
  The included scripts generate EasyDRAM RowClone traces, run Ramulator2 simulations, and parse the resulting statistics.

- **`easydram-programs/`**  
  A collection of benchmarks and programs used for testing and evaluating EasyDRAM.  
  For example:
  - `tests/easymemory/simload_basic/` contains a simple program that initializes the software memory controller on simulated workload processors and performs a copy from a source array to a destination array using EasyDRAM.

## Generating an EasyDRAM Bitstream

If you prefer to generate your own bitstream from source, we provide instructions below. If not, you may use the prebuilt bitstreams in `prebuilt/VCU108/`.

EasyDRAM builds on the [Chipyard framework](https://chipyard.readthedocs.io/en/latest/index.html). A bitstream for the AMD/Xilinx VCU108 board can be generated in 2 steps:

1. `cd easydram-chipyard/fpga/`
2. `make SUB_PROJECT=vcu108easyboom bitstream`

Please see the [relevant chapter in Chipyard documentation](https://chipyard.readthedocs.io/en/latest/Prototyping/General.html#generating-a-bitstream) for more details.

## Reproducing the Results in the Paper

We provide a [send_benchmark.sh](easydram-programs/tests/easymemory/send_benchmark.sh) script to easily run an experiment using EasyDRAM.

Executing the following command runs the `trisolv` workload from Polybench in an EasyDRAM system programmed with the [basic FRFC-FS memory controller](easydram-programs/tests/easymemory/frfcfs_mc/main.cpp) design in the FPGA board.

```bash
./send_benchmark.sh /dev/ttyUSB0 frfcfs_mc trisolv EasyDRAMRocketConfig
```

Detailed instructions on using the script are provided [here](easydram-programs/tests/easymemory/README.md).

## Citation

If you use this infrastructure in your work, please cite our DSN 2025 paper:

```bibtex
@inproceedings{canpolat2025easydram,
  title     = {{EasyDRAM: An FPGA-based Infrastructure for Fast and Accurate End-to-End Evaluation of Emerging DRAM Techniques}},
  author    = {Canpolat, Oğuzhan and Olgun, Ataberk and Novo, David and Ergin, Oğuz and Mutlu, Onur},
  booktitle = {DSN},
  year      = {2025}
}
```

## Contacts

Oğuzhan Canpolat (aqwoguz [at] gmail [dot] com)

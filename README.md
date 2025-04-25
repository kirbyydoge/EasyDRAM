**EasyDRAM: An FPGA-based Infrastructure for Fast and Accurate End-to-End Evaluation of Emerging DRAM Techniques**  
_To appear at DSN 2025_

EasyDRAM is an open-source infrastructure that enables fast and accurate end-to-end evaluation of DRAM techniques on FPGAs. This repository contains the full source code for EasyDRAM, including hardware modules, simulation infrastructure, and benchmarks.

## Repository Structure

- **`easydram-chipyard/`**  
  A modified version of [Chipyard](https://chipyard.readthedocs.io/en/latest/) that includes EasyDRAM integration.
  - **`generators/easydram/`**  
    This directory contains the main RTL implementation of EasyDRAM.

- **`easydram-ramulator/`**  
  Contains our Ramulator-based evaluation infrastructure for DRAM techniques.  
  This version includes our evaluation of **RowClone** within Ramulator 2.0.  
  _Our PolyBench traces and SolarDRAM implementation will be released soon._

- **`easydram-programs/`**  
  A collection of benchmarks and programs used for testing and evaluating EasyDRAM.  
  For example:
  - `tests/easymemory/simload_basic/` contains a simple program that initializes the software memory controller on simulated workload processors and performs a copy from a source array to a destination array using EasyDRAM.

## Citation

If you use this infrastructure in your work, please cite our DSN 2025 paper:

```bibtex
@inproceedings{canpolat2025easydram,
  title     = {{EasyDRAM: An FPGA-based Infrastructure for Fast and Accurate End-to-End Evaluation of Emerging DRAM Techniques}},
  author    = {Canpolat, Oğuzhan and Olgun, Ataberk and Novo, David and Ergin, Oğuz and Mutlu, Onur},
  booktitle = {DSN},
  year      = {2025}
}
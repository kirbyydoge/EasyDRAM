import os
import sys
import time
import subprocess
from concurrent.futures import ThreadPoolExecutor

NUM_THREADS = 8
VERILATOR_BASE_DIR = "/home/kirbyydoge/github/easydram-chipyard/sims/verilator"
SIM_RESULT_DIR = "/home/hdd/chipyard_sims"
PROG_BASE_DIR = "/home/kirbyydoge/github/easyprogs/install/riscv-bmarks"
CMD_OUT_DIR = f"{os.getcwd()}/verify"
DRAMSIM_INI_DIR = "/home/kirbyydoge/github/easydram-chipyard/generators/testchipip/src/main/resources/dramsim2_ini"

BASE_CFG = "EasyDRAMRocketConfig"
VERIFY_CFG = "EasyDRAMRocketVerifyConfig"

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
    "jacobi-1d", "jacobi-2d", "fdtd-2d"
]

CONFIG_LIST = [
    BASE_CFG,
    VERIFY_CFG,
]

# SIM_CMD_ARGS = [
#     "{simulation_path}/simulator-chipyard.harness-{config}",
#     "+permissive",
#     "+dramsim +dramsim_ini_dir={dramsim_ini_dir} +max_cycles=100000000",
#     "+permissive-off",
#     "{binary_dir}/{binary_name}.riscv",
#     " ",
#     "</dev/null 2> >(spike-dasm > {res_path}/chipyard.harness.TestHarness.{config}/{binary_name}.out)" +\
#     "| tee {res_path}/chipyard.harness.TestHarness.{config}/{binary_name}.log",
# ]

# def make_sim_command(program, config, out_path=""):
#     rindex_slash = program.rindex('/')
#     binary_dir = program[:rindex_slash]
#     binary_name = program[rindex_slash+1:]
#     sim_cmd = " \\\n\t".join(SIM_CMD_ARGS).format(
#         simulation_path=VERILATOR_BASE_DIR,
#         dramsim_ini_dir=DRAMSIM_INI_DIR,
#         res_path=SIM_RESULT_DIR,
#         config=config,
#         binary_dir=binary_dir,
#         binary_name=binary_name
#     )
#     return sim_cmd

SIM_CMD_ARGS = [
    "time {simulation_path}/simulator-chipyard.harness-{config}",
    "+permissive +max_cycles=10000000000 +permissive-off",
    "{binary_dir}/{binary_name}.riscv",
]

def make_sim_command(program, config, out_path=""):
    rindex_slash = program.rindex('/')
    binary_dir = program[:rindex_slash]
    binary_name = program[rindex_slash+1:]
    sim_cmd = " ".join(SIM_CMD_ARGS).format(
        simulation_path=VERILATOR_BASE_DIR,
        config=config,
        binary_dir=binary_dir,
        binary_name=binary_name
    )
    if out_path != "":
        sim_cmd = f"({sim_cmd}) > {out_path} 2>&1"
    return sim_cmd

def get_all_sim_commands(programs, configs):
    sim_cmd_list = []
    for prog in programs:
        for cfg in configs:
            sim_cmd_list.append((make_sim_command(f"{PROG_BASE_DIR}/{prog}", cfg), f"{CMD_OUT_DIR}/{prog}-{cfg}.out"))
    return sim_cmd_list

def run_simulations(sim_cmd_list):
    with ThreadPoolExecutor(max_workers=NUM_THREADS) as executor:
        def run_command(cmd_pair):
            cmd, out_path = cmd_pair
            os.system(f"echo \"Running: {cmd}\"")
            with open(out_path, "w", encoding="utf-8") as f:
                subprocess.Popen(cmd, shell=True, stdout=f, stderr=f).wait()
        executor.map(run_command, sim_cmd_list)

def main():
    sim_cmd_list = get_all_sim_commands(PROGRAM_LIST, CONFIG_LIST)

    if not os.path.exists(CMD_OUT_DIR):
        os.makedirs(CMD_OUT_DIR)

    run_simulations(sim_cmd_list)

if __name__ == "__main__":
    main()

import os
import pandas as pd

from easy_trace_cfg import *

STAT_PATH = "/home/kirbyydoge/github/easydram-ramulator/ae_results/rowclone/Dummy/stats"
LBL_CPU = "RAMCPU"
LBL_RC = "RAMRC"

def get_cycles(file):
    with open(file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line.startswith("cycles_recorded_core_0"):
                continue
            cycles = int(line.split(":")[1])
            return cycles
    return -1

expected_size = len(TEST_CASE_SIZES) * 2
df = pd.DataFrame(index=range(expected_size), columns=["label", "size", "initialize", "eviction", "copy", "overall"])
df_index = 0

test_case_kbs = [test_size // 1024 for test_size in TEST_CASE_SIZES]

for test_size in test_case_kbs:
    init_cycles = get_cycles(f"{STAT_PATH}/0_easy.cpu_init_{test_size}KB.txt")
    copy_cycles = get_cycles(f"{STAT_PATH}/0_easy.cpu_copy_{test_size}KB.txt")
    overall_cycles = init_cycles + copy_cycles
    df.iloc[df_index] = [LBL_CPU, test_size, init_cycles, 0, copy_cycles, overall_cycles]
    df_index += 1

for test_size in test_case_kbs:
    init_cycles = get_cycles(f"{STAT_PATH}/0_easy.rc_init_{test_size}KB.txt")
    flush_cycles = get_cycles(f"{STAT_PATH}/0_easy.rc_flush_{test_size}KB.txt")
    copy_cycles = get_cycles(f"{STAT_PATH}/0_easy.rc_copy_{test_size}KB.txt")
    overall_cycles = init_cycles + flush_cycles + copy_cycles
    df.iloc[df_index] = [LBL_RC, test_size, init_cycles, flush_cycles, copy_cycles, overall_cycles]
    df_index += 1

df = df[:df_index]
df.to_csv("dump.csv", index=False)
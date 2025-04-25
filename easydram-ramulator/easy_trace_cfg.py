LLC_BYTES_PER_CORE = 2 * 1024 * 1024 # 2 MiB
NUM_CORES = 4
LLC_ASSOC = 16
CACHELINE_BYTES = 64
BUBBLE_COUNT = 4
ROW_BYTES = 1024
ROBARACOCH_ROW_OFF = 18
ROBARACOCH_BANK_OFF = 13
ROBARACOCH_RANK_OFF = 12

TOTAL_BANKS = 32

N_RANKS = 2
N_BANKGROUPS = 8
N_BANKSPERGROUP = 4

TEST_CASE_SIZES = [(2**i) * 1024 for i in range(3, 15)]
CPU_REGISTER_BYTES = 8
SUBARRAY_SIZE = 256

DUMP_FOLDER = "./cputraces"

def make_tracename(is_cpu, is_copy, data_size):
    op_type = "cpu" if is_cpu else "rc"
    bench_type = "copy" if is_copy else "init"
    num_kilobytes = data_size // 1024
    return f"easy.{op_type}_{bench_type}_{num_kilobytes}KB"
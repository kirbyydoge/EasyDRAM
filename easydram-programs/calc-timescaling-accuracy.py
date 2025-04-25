import os

BASE_CFG = "EasyDRAMRocketConfig"
VERIFY_CFG = "EasyDRAMRocketVerifyConfig"

CMD_OUT_DIR = f"{os.getcwd()}/verify"

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

def get_cycles(prog, cfg):
    cycles = -1
    with open(f"{CMD_OUT_DIR}/{prog}-{cfg}.out", "r", encoding="utf-8") as f:
        for line in f:
            if "!#POLYRES#!" not in line:
                continue
            cycles = int(line.split()[1].split(":")[1])

    return cycles

def main():
    total_error = 0
    total_count = 0
    max_error = 0
    for prog in PROGRAM_LIST:
        base_cycle = get_cycles(prog, BASE_CFG)
        verify_cycle = get_cycles(prog, VERIFY_CFG)
        if base_cycle < 0 or verify_cycle < 0:
            continue
        error = abs((verify_cycle - base_cycle) / verify_cycle)
        print(f"Base: {base_cycle} Verify: {verify_cycle} Error: {error}")
        max_error = max(max_error, error)
        total_error += error
        total_count += 1
    print(f"Across {total_count} sims. Avg. Err (%): {total_error / total_count * 100:.2f} Max. Err (%): {max_error * 100}")


if __name__ == "__main__":
    main()
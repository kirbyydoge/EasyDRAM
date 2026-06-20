#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 serial_port"
    exit 1
fi

PORT=${1}
SIZE="${2}"
MC="rc_mc"
PROG="cpu_copy"
CONFIG="EasyBoomVCU108Config"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROGRAMS_DIR="${REPO_ROOT}/easydram-programs"

CHIPYARD_BASE="${CHIPYARD_BASE:-${REPO_ROOT}/easydram-chipyard}"
SRC_BASE="${CHIPYARD_BASE}/fpga/generated-src"
BOARD="${BOARD:-vcu108}"
HARNESS="${HARNESS:-VCU108FPGATestHarness}"
BIT_PATH="${SRC_BASE}/chipyard.fpga.${BOARD}.${HARNESS}.${CONFIG}/obj/${HARNESS}.bit"

set -e
 
make clean PROG=$MC
make PROG=$MC


for pow in {3..15}; do
    for rpt in {0..4}; do 
        make clean PROG=$PROG
        make PROG=$PROG CARG=-DTEST_BYTES=$((2**pow * 1024))

        vivado -mode tcl -nolog -nojournal -source program_fpga.tcl -tclargs "${BIT_PATH}"

        sudo python3 send_alone.py "${MC}/main.hex" EASYMEMC --port $PORT
        sudo python3 send_result.py "${PROG}/main.hex" EASYPROG --port $PORT --save out.txt --resfile cpucopy.txt --safeword '!#CLONERES#! '
    done
done
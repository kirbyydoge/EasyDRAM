#!/bin/bash

if [ "$#" -ne 4 ]; then
    echo "Usage: $0 serial_port prog0 prog1 config"
    exit 1
fi

PORT=${1}
MC="${2%/}"
PROG="${3%/}"
CONFIG=$4

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
make clean PROG=$PROG
make PROG=$PROG

vivado -mode tcl -nolog -nojournal -source program_fpga.tcl -tclargs "${BIT_PATH}"

sudo python3 send_alone.py "${MC}/main.hex" EASYMEMC --port $PORT
sudo python3 send_listen.py "${PROG}/main.hex" EASYPROG --port $PORT --save out.txt
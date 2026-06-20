#!/usr/bin/env bash

# wrapper to log output from init-submodules script

set -e
set -o pipefail

RDIR=$(dirname "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")")
cd "$RDIR"

./scripts/init-submodules-no-riscv-tools-nolog.sh "$@" 2>&1 | tee init-submodules-no-riscv-tools.log

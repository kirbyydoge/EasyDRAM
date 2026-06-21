#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

DEFAULT_BENCHMARK_DIR="$(cd ../.. && pwd)/install/riscv-bmarks"
echo "Writing benchmarks to: ${BENCHMARK_DIR:-${DEFAULT_BENCHMARK_DIR}}"

for dir in $(find . -type d); do
    if [[ -f "${dir}/Makefile" ]]; then
        echo "Running make -B in ${dir}"
        (cd "${dir}" && make -B)
    fi
done

echo "Logging Benchmarks:"

for dir in $(find . -type d); do
    if [[ -f "${dir}/Makefile" ]]; then
        endpoint_dir=$(basename "${dir}")
        echo "${endpoint_dir}"
    fi
done

#!/bin/bash

for dir in $(find . -type d); do
    if [[ -f "${dir}/Makefile" ]]; then
        echo "Running make -B in ${dir}"
        (cd "${dir}" && make -B)
    fi
done

echo "Logging Becnhmarks:"

for dir in $(find . -type d); do
    if [[ -f "${dir}/Makefile" ]]; then
        endpoint_dir=$(basename "${dir}")
        echo "${endpoint_dir}"
    fi
done
#! /bin/bash

echo "[INFO] Installing Python dependencies"
python3 -m pip install -r requirements.txt

echo "[INFO] Building Ramulator2"
rm -rf ./build/
sh "./build.sh"

echo "[INFO] Generating EasyDRAM RowClone traces"
python3 ./gen_rowclone_traces.py

echo "[INFO] Running the simple test simulation"
./ramulator2 -f base_config.yaml

rm -f ./test.cmds1

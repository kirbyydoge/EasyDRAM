#! /bin/bash

echo "[INFO] Installing Python dependencies"
pip3 install -r requirements.txt

echo "[INFO] Building Ramulator2"
rm -rf ./build/
sh "./build.sh"

echo "[INFO] Generating EasyDRAM RowClone traces"
python3 ./gen_rowclone_traces.py

echo "[INFO] Running the simple test simulation"
./ramulator2 -f base_config.yaml

rm ./test.cmds1

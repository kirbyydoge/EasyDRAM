#! /bin/bash

echo "[INFO] Checking RowClone simulations"
python3 -m scripts.run_parser "$PWD" "$PWD/mixes/rowclone.mix" "$PWD/ae_results/rowclone" 4

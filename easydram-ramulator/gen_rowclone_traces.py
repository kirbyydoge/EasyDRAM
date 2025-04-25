from easy_trace_cfg import *

# Generate CPU Copy
for data_bytes in TEST_CASE_SIZES:
    n_rows = int(data_bytes / ROW_BYTES)
    row_pairs = [(i, n_rows+i) for i in range(n_rows)]

    with open(f"{DUMP_FOLDER}/{make_tracename(True, True, data_bytes)}", "w", encoding="utf-8") as f:
        inst_ctr = 0
        for src, dst in row_pairs:
            src_row_base = src << ROBARACOCH_ROW_OFF
            dst_row_base = dst << ROBARACOCH_ROW_OFF
            for col_idx in range(0, ROW_BYTES, CACHELINE_BYTES):
                f.write(f"{inst_ctr} READ {src_row_base + col_idx}\n")
                f.write(f"{inst_ctr + 1} READ {dst_row_base + col_idx}\n")
                f.write(f"{inst_ctr + 2} WRITE {dst_row_base + col_idx} : {inst_ctr} {inst_ctr + 1}\n")
                inst_ctr += 3
        f.write(f"{inst_ctr} COMP : {inst_ctr - 1}\n")

# Generate RowClone Copy
for data_bytes in TEST_CASE_SIZES:
    n_rows = int(data_bytes / ROW_BYTES)
    row_pairs = [(0, n_rows+i+1) for i in range(n_rows)]

    with open(f"{DUMP_FOLDER}/{make_tracename(False, True, data_bytes)}", "w", encoding="utf-8") as f:
        inst_ctr = 0

        for src, dst in row_pairs:
            src_row_base = src << ROBARACOCH_ROW_OFF
            dst_row_base = dst << ROBARACOCH_ROW_OFF
            f.write(f"{inst_ctr} ROWC {src_row_base + col_idx} {dst_row_base + col_idx}\n")
            inst_ctr += 1

        f.write(f"{inst_ctr} COMP : {inst_ctr - 1}\n")

# Generate CPU Init
for data_bytes in TEST_CASE_SIZES:
    n_rows = int(data_bytes / ROW_BYTES)
    row_pairs = [(0, i+1) for i in range(n_rows)]

    with open(f"{DUMP_FOLDER}/{make_tracename(True, False, data_bytes)}", "w", encoding="utf-8") as f:
        inst_ctr = 0
        for src, dst in row_pairs:
            src_row_base = src << ROBARACOCH_ROW_OFF
            dst_row_base = dst << ROBARACOCH_ROW_OFF
            for col_idx in range(0, ROW_BYTES, CACHELINE_BYTES):
                f.write(f"{inst_ctr} READ {dst_row_base + col_idx}\n")
                f.write(f"{inst_ctr + 1} WRITE {dst_row_base + col_idx} : {inst_ctr}\n")
                inst_ctr += 2
        f.write(f"{inst_ctr} COMP : {inst_ctr - 1}\n")

# Generate RowClone Init
for data_bytes in TEST_CASE_SIZES:
    init_rows = max(1, int(data_bytes / ROW_BYTES / SUBARRAY_SIZE)) 
    row_pairs = [(init_rows, init_rows+i+1) for i in range(int(data_bytes / ROW_BYTES))]

    with open(f"{DUMP_FOLDER}/{make_tracename(False, False, data_bytes)}", "w", encoding="utf-8") as f:
        inst_ctr = 0

        for seed in range(init_rows):
            init_row_base = seed << ROBARACOCH_ROW_OFF
            for col_idx in range(0, ROW_BYTES, CACHELINE_BYTES):
                f.write(f"{inst_ctr} READ {init_row_base + col_idx}\n")
                f.write(f"{inst_ctr + 1} WRITE {init_row_base + col_idx} : {inst_ctr}\n")
                inst_ctr += 2

        for src, dst in row_pairs:
            src_row_base = src << ROBARACOCH_ROW_OFF
            dst_row_base = dst << ROBARACOCH_ROW_OFF
            f.write(f"{inst_ctr} ROWC {src_row_base + col_idx} {dst_row_base + col_idx}\n")
            inst_ctr += 1

        f.write(f"{inst_ctr} COMP : {inst_ctr - 1}\n")

# Generate RowClone Flush
for data_bytes in TEST_CASE_SIZES:
    init_rows = max(1, int(data_bytes / ROW_BYTES)) 
    row_pairs = [(init_rows, init_rows+i+1) for i in range(int(data_bytes / ROW_BYTES))]

    with open(f"{DUMP_FOLDER}/{make_tracename(False, False, data_bytes).replace('init', 'flush')}", "w", encoding="utf-8") as f:
        inst_ctr = 0

        for seed in range(init_rows):
            init_row_base = seed << ROBARACOCH_ROW_OFF
            for col_idx in range(0, ROW_BYTES, CACHELINE_BYTES):
                f.write(f"{inst_ctr} READ {init_row_base + col_idx}\n")
                inst_ctr += 2

        for src, dst in row_pairs:
            src_row_base = src << ROBARACOCH_ROW_OFF
            dst_row_base = dst << ROBARACOCH_ROW_OFF
            f.write(f"{inst_ctr} ROWC {src_row_base + col_idx} {dst_row_base + col_idx}\n")
            inst_ctr += 1

        f.write(f"{inst_ctr} COMP : {inst_ctr - 1}\n")
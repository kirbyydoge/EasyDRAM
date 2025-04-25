import sys

def process_line(line: str):
    fields = {}
    tokens = line.split()
    name_begin = tokens[0].rfind("/")
    name = tokens[0][name_begin+1:len(tokens[0])-5]
    for token in tokens[1:]:
        sub_tokens = token.split(':')
        key, value = sub_tokens[0], sub_tokens[1]
        fields[key] = value
    return (name, fields)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python3 result_to_csv.py <sys_name> <mc_name> <res_file>")
        exit(0)

    sys_name = sys.argv[1]
    mc_name = sys.argv[2]
    res_file = sys.argv[3]

    with open(res_file, "r", encoding="utf-8") as f:
        processed = [process_line(line) for line in f.readlines()]

        # print("System, Benchmark, Scheduler, Key, Value")
        for benchmark, fields in processed:
            for key in fields:
                print(f"{sys_name}, large_{benchmark}, {mc_name}, {key}, {fields[key]}")


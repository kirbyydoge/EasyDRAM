with open("scheduler.hex", "r") as f:
	prog_binary = [line.strip() for line in f.readlines()]

# Cleanup trailing zeros from the program
binary_len = len(prog_binary)
for i in range(binary_len):
	reversed_idx = binary_len - i - 1
	if prog_binary[reversed_idx] != "00":
		break
	del prog_binary[reversed_idx]

with open("embed.h", "w", encoding="utf-8") as embed:
	embed.write((
		"#ifndef EMBED_H_\n"
		"#define EMBED_H_\n"
		"#include <stdint.h>\n"
		"const uint8_t bin_program[] = {\n"
	))
	first = True
	byte_count = 0
	for line in prog_binary:	
		if first:
			first = False	
		else:
			embed.write(",")
			if byte_count % 16 == 0:
				embed.write("\n")
		byte_count = byte_count + 1
		embed.write(f"0x{line.strip()}")
	embed.write((
		"};\n"
		f"const int len_program = {len(prog_binary)};\n"
		"#endif //EMBED_H_\n"
	))

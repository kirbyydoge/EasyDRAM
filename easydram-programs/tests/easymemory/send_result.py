import serial
import argparse
import time

parser = argparse.ArgumentParser()

parser.add_argument('hex')
parser.add_argument('cmd')
parser.add_argument('--port', '-p', default='/dev/ttyUSB1')
parser.add_argument('--save', default=None)
parser.add_argument('--safeword', default="!#POLYRES#! ")
parser.add_argument('--resfile', default="results.txt")

args = parser.parse_args()

serial_port = args.port 
hex = args.hex
if hex == "":
    hex = None

save_file = args.save
res_file = args.resfile
safe_word = args.safeword

cmd = args.cmd

ser = serial.Serial(serial_port, 115200, timeout=None)

if hex:
    hex_f = open(hex)
    hex_arr = hex_f.readlines()
    hex_lines = len(hex_arr)
    line_chars = len(hex_arr[0])
    line_bytes = line_chars // 2
    hex_len =  hex_lines * line_bytes
    len_bytes = hex_len.to_bytes(4, "big")

ser.write(bytes(cmd, encoding="ASCII"))

if hex:
    for byte in len_bytes:
        # print(bytes([byte]))
        ser.write(bytes([byte]))

    print("Sending " + hex + "... [" + str(hex_len) + " bytes]")

    byte_cap = 0
    byte_counter = 0
    for line in hex_arr:
        while byte_counter >= byte_cap:
            rd = ser.read(1)
            allowance = int.from_bytes(rd, 'big')
            byte_cap += allowance
        hex_data = bytearray.fromhex(line)
        for byte in hex_data:
            # print(bytes([byte]))
            ser.write(bytes([byte]))
        byte_counter += line_bytes
        print("                                                              ", end="\r")
        print("%" + "{:.2f}".format((byte_counter / hex_len) * 100), end="\r", flush=True)

    print("Done               ")

if save_file:
    f = open(save_file, "w", encoding="utf-8")

try:
    line = ""
    is_running = True
    while is_running:
        chr = ser.read(1)
        chr_dcd = chr.decode('utf-8')
        line += chr_dcd
        print(chr_dcd, end="", flush=True)
        if save_file:
            f.write(chr_dcd)
            f.flush()
        if chr_dcd == "\n":
            if safe_word in line:
                with open(res_file, 'a', encoding="utf-8") as res_f:
                    res_f.write(f"{hex}: {line.replace(safe_word, '')}")
                    is_running = False
            line = ""
except Exception as e:
    print(e)
    pass

if save_file:
    f.close()
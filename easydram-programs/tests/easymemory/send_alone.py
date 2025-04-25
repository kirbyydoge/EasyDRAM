import serial
import argparse
import time

parser = argparse.ArgumentParser()

parser.add_argument('hex')
parser.add_argument('cmd')
parser.add_argument('--port', '-p', default='/dev/ttyUSB4')

args = parser.parse_args()

serial_port = args.port 
hex = args.hex
if hex == "":
    hex = None

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
        ser.write(bytes([byte]))

    print("Sending... [" + str(hex_len) + " bytes]")

    byte_cap = 0
    byte_counter = 0
    for line in hex_arr:
        while byte_counter >= byte_cap:
            rd = ser.read(1)
            allowance = int.from_bytes(rd, 'big')
            byte_cap += allowance
        hex_data = bytearray.fromhex(line)
        for byte in hex_data:
            ser.write(bytes([byte]))
        byte_counter += line_bytes
        print("                                                              ", end="\r")
        print("%" + "{:.2f}".format((byte_counter / hex_len) * 100), end="\r", flush=True)

    print("Done               ")

time.sleep(0.1)

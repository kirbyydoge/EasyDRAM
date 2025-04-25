import serial
import argparse
import time

parser = argparse.ArgumentParser()

parser.add_argument('hex')
parser.add_argument('cmd')
parser.add_argument('--port' , '-p', default='/dev/ttyUSB1')

args = parser.parse_args()

serial_port = args.port 
hex = args.hex
cmd = args.cmd

ser = serial.Serial(serial_port, 115200)

hex_f = open(hex)

hex_arr = hex_f.readlines()
hex_lines = len(hex_arr)
line_chars = len(hex_arr[0])
line_bytes = line_chars // 2
hex_len =  hex_lines * line_bytes
len_bytes = hex_len.to_bytes(4, "big")

ser.write(bytes(cmd, encoding="ASCII"))
print(hex_len)

for byte in len_bytes:
    print(bytes([byte]))
    ser.write(bytes([byte]))

print("Sending... [" + str(hex_len) + " bytes]")

byte_counter = 0
for line in hex_arr:
    time.sleep(0.005)
    hex_data = bytearray.fromhex(line)
    for byte in hex_data:
        # print(bytes([byte]))
        ser.write(bytes([byte]))
    byte_counter += line_bytes
    print("                                                              ", end = "\r")
    print("%" + "{:.2f}".format((byte_counter / hex_len) * 100), end = "\r", flush = True)

print("Done               ")

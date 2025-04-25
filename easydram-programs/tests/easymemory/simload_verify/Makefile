# compiler to use
CXX = riscv64-unknown-elf-g++
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -fno-threadsafe-statics -fno-common -fno-builtin-printf -specs=htif_nano.specs

main:
	$(CXX) $(CXXFLAGS) -c main.cpp -o main.o
	$(CXX) $(CXXFLAGS) -o main.riscv main.o
	$(CDUMP) -drwCS main.riscv > main_assembly.txt

clean:
	rm -rf *.txt *.riscv *.o *.hex
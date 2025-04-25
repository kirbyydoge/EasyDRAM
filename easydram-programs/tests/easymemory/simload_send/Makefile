# compiler to use
CXX = riscv64-unknown-elf-g++
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -fno-threadsafe-statics -fno-common -fno-builtin-printf -specs=htif_nano.specs

scheduler:
	$(CXX) $(CXXFLAGS) -c scheduler.cpp -o scheduler.o
	$(CXX) $(CXXFLAGS) -o scheduler.riscv scheduler.o
	$(CDUMP) -drwCS scheduler.riscv > scheduler_assembly.txt
	elf2hex 1 32768 scheduler.riscv 2147483648 > scheduler.hex
	cp ../embed.py .
	python3 embed.py
	rm embed.py

main: scheduler
	$(CXX) $(CXXFLAGS) -c main.cpp -o main.o
	$(CXX) $(CXXFLAGS) -o main.riscv main.o
	$(CDUMP) -drwCS main.riscv > main_assembly.txt

clean:
	rm -rf *.txt *.riscv *.o *.hex embed.h
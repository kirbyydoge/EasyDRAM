# Makefile

# compiler to use
CXX = riscv64-unknown-elf-g++
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -fno-threadsafe-statics -Wall -O3 -fno-common -fno-builtin-printf -specs=htif_nano.specs -I./common -I./common/include -I./common/include/devices

# target program
ifndef PROG
$(error PROG is not set)
endif

# target executable
TARGET = main

# directories
COMMON_DIR = ./common
OBJ_DIR = ./objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.cpp)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.cpp, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = main.cpp
OBJS = $(OBJ_DIR)/main.o

$(TARGET) : $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o $(PROG)/$@.riscv $^
	$(CDUMP) -drwCS $(PROG)/$@.riscv > $(PROG)/$@_assembly.txt
	elf2hex 1 65536 $(PROG)/$@.riscv 2147483648 > $(PROG)/$@.hex

$(OBJS) : $(PROG)/$(SRCS)
	$(CXX) $(CXXFLAGS) $(CARG) -c $< -o $@

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

.PHONY: clean

clean:
	rm -f $(OBJ_DIR)/*.o $(PROG)/$(TARGET).riscv $(PROG)/$(TARGET)_assembly.txt $(PROG)/$(TARGET).hex

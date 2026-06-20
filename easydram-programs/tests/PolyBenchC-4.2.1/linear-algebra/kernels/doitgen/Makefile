BENCHMARK_DIR ?= $(abspath $(COMMON_DIR)/../../../install/riscv-bmarks)

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin -specs=htif_nano.specs -I. -I../../../utilities/common -I../../../utilities

# target executable
TARGET = doitgen

# directories
COMMON_DIR = ../../../utilities
OBJ_DIR = ../../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = doitgen.c
OBJS = doitgen.o

doitgen: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o doitgen.riscv $^ -lm
	$(CDUMP) -d doitgen.riscv > doitgen.riscv.dump
	mkdir -p $(BENCHMARK_DIR)
	cp doitgen.riscv $(BENCHMARK_DIR)
	cp doitgen.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	mkdir -p $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: doitgen

clean:
	@ rm -f doitgen.riscv


BENCHMARK_DIR ?= $(abspath $(COMMON_DIR)/../../../install/riscv-bmarks)

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I../../../utilities/common -I../../../utilities

# target executable
TARGET = trmm

# directories
COMMON_DIR = ../../../utilities
OBJ_DIR = ../../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = trmm.c
OBJS = trmm.o

trmm: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o trmm.riscv $^ -lm
	$(CDUMP) -d trmm.riscv > trmm.riscv.dump
	mkdir -p $(BENCHMARK_DIR)
	cp trmm.riscv $(BENCHMARK_DIR)
	cp trmm.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	mkdir -p $(OBJ_DIR)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: trmm

clean:
	@ rm -f trmm.riscv


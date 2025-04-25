BENCHMARK_DIR=/home/kirbyydoge/github/easyprogs/install/riscv-bmarks

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I../../../utilities/common -I../../../utilities

# target executable
TARGET = syr2k

# directories
COMMON_DIR = ../../../utilities
OBJ_DIR = ../../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = syr2k.c
OBJS = syr2k.o

syr2k: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o syr2k.riscv $^ -lm
	$(CDUMP) -d syr2k.riscv > syr2k.riscv.dump
	cp syr2k.riscv $(BENCHMARK_DIR)
	cp syr2k.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: syr2k

clean:
	@ rm -f syr2k.riscv


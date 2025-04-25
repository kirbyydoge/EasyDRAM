BENCHMARK_DIR=/home/kirbyydoge/github/easyprogs/install/riscv-bmarks

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I../../../utilities/common -I../../../utilities

# target executable
TARGET = atax

# directories
COMMON_DIR = ../../../utilities
OBJ_DIR = ../../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = atax.c
OBJS = atax.o

atax: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o atax.riscv $^ -lm
	$(CDUMP) -d atax.riscv > atax.riscv.dump
	cp atax.riscv $(BENCHMARK_DIR)
	cp atax.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: atax

clean:
	@ rm -f atax.riscv


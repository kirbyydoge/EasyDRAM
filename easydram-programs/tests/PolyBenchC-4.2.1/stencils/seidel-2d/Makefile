BENCHMARK_DIR=/home/kirbyydoge/github/easyprogs/install/riscv-bmarks

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I../../utilities/common -I../../utilities

# target executable
TARGET = seidel-2d

# directories
COMMON_DIR = ../../utilities
OBJ_DIR = ../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = seidel-2d.c
OBJS = seidel-2d.o

seidel-2d: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o seidel-2d.riscv $^ -lm
	$(CDUMP) -d seidel-2d.riscv > seidel-2d.riscv.dump
	cp seidel-2d.riscv $(BENCHMARK_DIR)
	cp seidel-2d.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: seidel-2d

clean:
	@ rm -f seidel-2d.riscv


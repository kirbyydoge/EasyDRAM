BENCHMARK_DIR=/home/kirbyydoge/github/easyprogs/install/riscv-bmarks

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I../../utilities/common -I../../utilities

# target executable
TARGET = floyd-warshall

# directories
COMMON_DIR = ../../utilities
OBJ_DIR = ../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = floyd-warshall.c
OBJS = floyd-warshall.o

floyd-warshall: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o floyd-warshall.riscv $^ -lm
	$(CDUMP) -d floyd-warshall.riscv > floyd-warshall.riscv.dump
	cp floyd-warshall.riscv $(BENCHMARK_DIR)
	cp floyd-warshall.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: floyd-warshall

clean:
	@ rm -f floyd-warshall.riscv


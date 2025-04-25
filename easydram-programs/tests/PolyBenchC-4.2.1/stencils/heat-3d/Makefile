BENCHMARK_DIR=/home/kirbyydoge/github/easyprogs/install/riscv-bmarks

# compiler to use
CXX = riscv64-unknown-elf-gcc
CDUMP = riscv64-unknown-elf-objdump

# compiler flags
CXXFLAGS = -O2 -fno-common -fno-builtin-fprintf -fno-builtin-printf -specs=htif_nano.specs -I. -I../../utilities/common -I../../utilities

# target executable
TARGET = heat-3d

# directories
COMMON_DIR = ../../utilities
OBJ_DIR = ../../utilities/objects

# source and object files
COMMON_SRCS = $(wildcard $(COMMON_DIR)/*.c)
COMMON_OBJS = $(patsubst $(COMMON_DIR)/%.c, $(OBJ_DIR)/%.o, $(COMMON_SRCS))
SRCS = heat-3d.c
OBJS = heat-3d.o

heat-3d: $(OBJS) $(COMMON_OBJS)
	$(CXX) $(CXXFLAGS) -o heat-3d.riscv $^ -lm
	$(CDUMP) -d heat-3d.riscv > heat-3d.riscv.dump
	cp heat-3d.riscv $(BENCHMARK_DIR)
	cp heat-3d.riscv.dump $(BENCHMARK_DIR)

$(OBJS): $(SRCS)
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

$(COMMON_OBJS): $(OBJ_DIR)/%.o : $(COMMON_DIR)/%.c
	$(CXX) $(CXXFLAGS) -c $< -o $@ -lm

default: heat-3d

clean:
	@ rm -f heat-3d.riscv


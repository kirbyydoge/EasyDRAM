#ifndef     RAMULATOR_FRONTEND_PROCESSOR_DEP_CORE_H
#define     RAMULATOR_FRONTEND_PROCESSOR_DEP_CORE_H

#include <vector>
#include <string>
#include <functional>
#include <filesystem>
#include <fstream>
#include <vector>
#include <unordered_set>

#include "base/type.h"
#include "base/request.h"
#include "translation/translation.h"

namespace Ramulator {

class DEPO3LLC;

class DEPO3Core: public Clocked<DEPO3Core> {
public:
    friend class DEPO3;

    enum class InstType {
        READ = 0,
        WRITE,
        COMP,
        WBACK,
        ROWC
    };

    struct Inst {
        uint64_t id;
        InstType type;
        Addr_t tgt_addr;
        std::vector<uint64_t> dependencies;
        int dependencies_done;
        std::vector<int> dependees;
        bool ready;
        Addr_t src_addr; // Reserved for RowClone
    };

    struct ReqFIFOEntry {
        Addr_t addr;
        int rob_idx;
        InstType type;
    };

    struct OnFlightEntry {
        Clk_t depart;
        int rob_idx;
    };
    
    class Trace {
    public:
        std::vector<Inst> m_trace;
        std::queue<Inst> m_insts;
        size_t m_buffer_size = 0;
        size_t m_trace_length = 0;
        size_t m_curr_trace_idx = 0;
        bool m_full_load = true;
        Inst last_inst;

        Trace(std::string file_path_str, size_t max_buffer);
        const Inst& get_next_inst();
    
    private:
        std::filesystem::path m_trace_path;
        std::ifstream m_trace_file;

        InstType parse_inst_type(const std::string& type);
        void full_load();
        void chunk_load(size_t load_count);
        Inst parse_line(const std::string& line);
        void reset_trace_file();
    };

    class ReorderBuffer {
    public:
        ReorderBuffer(int id, int retire_width, int depth, std::queue<ReqFIFOEntry>& issuable_reqs);      
        bool is_full();
        bool insert(const Inst& inst);
        int retire();
        void set_ready(int rob_idx);
    
    private:
        void update_dependents(const Inst& inst, int rob_idx);

    private:
        int m_id;

        int m_retire_width;          
        int m_depth;      

        std::vector<Inst> m_rob;
        int m_head_idx;
        int m_tail_idx; 
        bool m_empty;

        std::queue<ReqFIFOEntry>& m_issuable_reqs;
    };

private:
    int m_id = -1;
    int m_fetch_width = 4;
    int m_total_fetched = 0;

    Trace m_trace;
    ReorderBuffer m_rob;
    ITranslation* m_translation;
    DEPO3LLC* m_llc;
    std::queue<ReqFIFOEntry> m_issuable_reqs;
    std::unordered_map<Addr_t, OnFlightEntry> m_onflight_reqs;

    std::function<void(Request&)> m_callback;

    size_t m_num_expected_insts = 0;  
    uint64_t m_num_max_cycles = 0;
    Clk_t m_last_mem_cycle = 0;
    int m_lat_hist_sens = 0;
    std::unordered_map<int, uint64_t> m_lat_histogram;
    std::filesystem::path m_dump_path;

    bool m_is_attacker = false;

    void dump_latency_histogram();

/************************************************
 *              Core Statistics
 ***********************************************/
public:
    bool reached_expected_num_insts = false;
    size_t s_insts_retired = 0; 
    size_t s_cycles_recorded = 0; 
    size_t s_insts_recorded = 0;
    Clk_t  s_mem_access_cycles = 0; 
    size_t s_mem_requests_issued = 0;

public:
    DEPO3Core(
        int id,
        int ipc,
        int depth,
        size_t num_expected_insts,
        uint64_t num_max_cycles,
        std::string trace_path,
        ITranslation* translation,
        DEPO3LLC* llc,
        int lat_hist_sens,
        std::string& dump_path,
        bool is_attacker,
        Request::SpecType spec_type,
        int max_trace_buf
    );

    void tick() override;
    void receive(Request& req);
};

}        // namespace Ramulator


#endif   // RAMULATOR_FRONTEND_PROCESSOR_DEP_CORE_H

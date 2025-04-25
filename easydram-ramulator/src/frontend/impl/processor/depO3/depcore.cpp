#include <filesystem>
#include <iostream>
#include <fstream>

#include <spdlog/spdlog.h>

#include "base/exception.h"
#include "base/utils.h"
#include "frontend/impl/processor/depO3/depcore.h"
#include "frontend/impl/processor/depO3/depllc.h"

// #define DEBUG_DEPO

#ifdef DEBUG_DEPO
    #define DEBUG(fmt, ...) std::printf(fmt, ##__VA_ARGS__)
#else
    #define DEBUG(fmt, ...) do {} while (0)
#endif

namespace Ramulator {

DEPO3Core::Trace::Trace(std::string file_path_str, size_t max_buffer) {
    m_trace_path = std::filesystem::path(file_path_str);
    if (!std::filesystem::exists(m_trace_path)) {
        throw ConfigurationError("Trace {} does not exist!", file_path_str);
    }

    m_trace_file = std::ifstream(m_trace_path);
    if (!m_trace_file.is_open()) {
        throw ConfigurationError("Trace {} cannot be opened!", file_path_str);
    }
    
    m_buffer_size = max_buffer;

    m_trace_length = 0;
    std::string line;
    while (std::getline(m_trace_file, line)) {
        m_trace_length++;
    }

    reset_trace_file();

    if (m_trace_length < m_buffer_size) {
        m_full_load = true;
        full_load();
    }
    else {
        m_full_load = false;
        chunk_load(m_buffer_size);
    }
}

void DEPO3Core::Trace::full_load() {
    std::string line;
    while (std::getline(m_trace_file, line)) {
        m_trace.emplace_back(std::move(parse_line(line)));
    }
    m_trace_file.close();
}

void DEPO3Core::Trace::chunk_load(size_t load_count) {
    std::string line;
    size_t loaded_insts = 0;
    while (loaded_insts < load_count) {
        if (m_trace_file.eof()) {
            reset_trace_file();
        }
        while (loaded_insts < load_count && std::getline(m_trace_file, line)) {
            m_insts.push(std::move(parse_line(line)));
            loaded_insts++;
        }
    }
}

DEPO3Core::Inst DEPO3Core::Trace::parse_line(const std::string& line) {
    std::istringstream iss(line);

    Inst inst;
    iss >> inst.id;

    std::string type;
    iss >> type;
    inst.type = parse_inst_type(type);

    inst.src_addr = 0;
    if (inst.type == InstType::ROWC) {
        Addr_t src_addr;
        iss >> src_addr;
        inst.src_addr = src_addr;
    }

    inst.tgt_addr = 0;
    if (inst.type != InstType::COMP) {
        Addr_t tgt_addr;
        iss >> tgt_addr;
        inst.tgt_addr = tgt_addr;
    }

    inst.dependencies.clear();
    inst.dependencies_done = 0;
    inst.dependees.clear();
    std::string dep_marker;
    if (iss >> dep_marker && dep_marker == ":") {
        uint64_t dep;
        while (iss >> dep) {
            inst.dependencies.push_back(dep);
        }
    }

    return inst;
}

DEPO3Core::InstType DEPO3Core::Trace::parse_inst_type(const std::string& type) {
    if (type == "READ") return InstType::READ;
    if (type == "WRITE") return InstType::WRITE;
    if (type == "COMP") return InstType::COMP;
    if (type == "WBACK") return InstType::WBACK;
    if (type == "ROWC") return InstType::ROWC;
    throw std::invalid_argument("[DEPTRACE] Unknown instruction type: " + type);
}

const DEPO3Core::Inst& DEPO3Core::Trace::get_next_inst() {
    if (m_full_load) {
        const Inst& inst = m_trace[m_curr_trace_idx];
        m_curr_trace_idx = (m_curr_trace_idx + 1) % m_trace_length;
        return inst;
    }
    if (m_insts.empty()) {
        chunk_load(m_buffer_size);
    }
    last_inst = m_insts.front();
    m_insts.pop();
    return last_inst;
}

void DEPO3Core::Trace::reset_trace_file() {
    m_trace_file.clear();
    m_trace_file.seekg(0);
}

DEPO3Core::ReorderBuffer::ReorderBuffer(int id, int retire_width, int depth, std::queue<ReqFIFOEntry>& issuable_reqs):
    m_id(id), m_retire_width(retire_width), m_depth(depth), m_issuable_reqs(issuable_reqs),
    m_head_idx(0), m_tail_idx(0), m_empty(true) {

    m_rob.resize(m_depth);
}

bool DEPO3Core::ReorderBuffer::is_full() {
    return !m_empty && m_head_idx == m_tail_idx;
}

bool DEPO3Core::ReorderBuffer::insert(const Inst& inst) {
    if (is_full()) {
        return false;
    }
    if (inst.type == InstType::WBACK) {
        m_issuable_reqs.push({inst.tgt_addr, -1, inst.type});
        return true;
    }
    m_rob[m_tail_idx] = inst;
    Inst& entry = m_rob[m_tail_idx];
    bool is_ready = true;
    if (!m_empty) {
        uint64_t rob_head_id = m_rob[m_head_idx].id;
        for (uint64_t dep: inst.dependencies) {
            int rob_off = dep - rob_head_id;
            int rob_idx = (m_head_idx + rob_off) % m_depth;
            if (dep < rob_head_id || m_rob[rob_idx].ready) {
                entry.dependencies_done++;
                continue;
            }
            m_rob[rob_idx].dependees.push_back(m_tail_idx);
            is_ready = false;
        }
    }
    if (is_ready && inst.type != InstType::COMP) {
        m_issuable_reqs.push({inst.tgt_addr, m_tail_idx, inst.type});
        is_ready = inst.type == InstType::WRITE;
    }
    entry.ready = is_ready;
    DEBUG("[DEPCORE:%d] Inserting %ld (%s %s) to ROB[%d]\n",
        m_id,
        inst.id,
        inst.type == InstType::COMP ? "COMP" :
            inst.type == InstType::READ ? "READ" :
            inst.type == InstType::ROWC ? "ROWC" : "WRITE",
        is_ready ? "READY" : "NOT READY",
        m_tail_idx
    );
    m_tail_idx = (m_tail_idx + 1) % m_depth;
    m_empty = false;
    return true;
}

int DEPO3Core::ReorderBuffer::retire() {
    int num_retired = 0;
    for (int i = 0; i < m_retire_width && !m_empty; i++) {
        const Inst& rob_head = m_rob[m_head_idx];
        if (!rob_head.ready) {
            break;
        }
        DEBUG("[DEPCORE:%d] Retiring %ld\n", m_id, rob_head.id);
        update_dependents(rob_head, m_head_idx);
        m_head_idx = (m_head_idx + 1) % m_depth;
        m_empty = m_head_idx == m_tail_idx;
        num_retired++;
    }
    return num_retired;
}

void DEPO3Core::ReorderBuffer::set_ready(int rob_idx) {
    if (rob_idx < 0) {
        return;
    }
    Inst& rob_entry = m_rob[rob_idx];
    rob_entry.ready = true;
    update_dependents(rob_entry, rob_idx);
}

void DEPO3Core::ReorderBuffer::update_dependents(const Inst& inst, int rob_idx) {
    for (int dependee_idx: inst.dependees) {
        Inst& dependee = m_rob[dependee_idx];
        dependee.dependencies_done++;
        if (dependee.dependencies.size() != dependee.dependencies_done) {
            continue;
        }
        DEBUG("[DEPCORE:%d] Dependency resolved for %ld\n", m_id, dependee.id);
        dependee.ready = dependee.type != InstType::READ;
        if (dependee.type != InstType::COMP) {
            DEBUG("[DEPCORE:%d] Pushing %ld to request FIFO\n", m_id, dependee.tgt_addr);
            m_issuable_reqs.push({dependee.tgt_addr, dependee_idx, dependee.type});
        }
        update_dependents(dependee, dependee_idx);
    }
}

DEPO3Core::DEPO3Core(int id, int ipc, int depth, size_t num_expected_insts,
    uint64_t num_max_cycles, std::string trace_path, ITranslation* translation,
    DEPO3LLC* llc, int lat_hist_sens, std::string& dump_path, bool is_attacker,
    Request::SpecType spec_type, int max_trace_buf):
m_id(id), m_rob(id, ipc, depth, m_issuable_reqs), m_trace(trace_path, max_trace_buf),
m_num_expected_insts(num_expected_insts), m_num_max_cycles(num_max_cycles), m_translation(translation),
m_llc(llc), m_lat_hist_sens(lat_hist_sens) {
    // Fetch the instructions and addresses for tick 0
    m_dump_path = "";
    if (dump_path == "") {
        return;
    }
    m_dump_path = fmt::format("{}.core{}", dump_path, id);
    auto parent_path = m_dump_path.parent_path();
    std::filesystem::create_directories(parent_path);
    if (!(std::filesystem::exists(parent_path) && std::filesystem::is_directory(parent_path))) {
        throw ConfigurationError("Invalid path to latency dump file: {}", parent_path.string());
    }
}

void DEPO3Core::tick() {
    m_clk++;

    auto retired = m_rob.retire();
    s_insts_retired += retired;

    if (!reached_expected_num_insts) {
        s_cycles_recorded = m_clk;
        s_insts_recorded = s_insts_retired;
        bool insts_done = m_num_expected_insts > 0 ?
            (s_insts_retired >= m_num_expected_insts) : (s_insts_retired >= m_trace.m_trace_length);
        bool cycles_done = m_clk >= m_num_max_cycles;
        if (insts_done || cycles_done) {
            dump_latency_histogram();
            reached_expected_num_insts = true;
        }
    }

    bool continue_fetching = m_num_expected_insts > 0 || m_total_fetched < m_trace.m_trace_length;
    int fetched_inst = 0;
    if (continue_fetching) {
        int fetch_count = std::min(m_fetch_width, (int) (m_trace.m_trace_length - m_total_fetched));
        for (int i = 0; i < fetch_count && !m_rob.is_full(); i++) {
            Inst inst = m_trace.get_next_inst();
            m_rob.insert(inst);
            fetched_inst++;
        }
        m_total_fetched += fetched_inst;
    }

    if (fetched_inst > 0) {
        DEBUG("[DEPCORE:%d] (%ld) Fetched %d instructions\n", m_id, m_clk, fetched_inst);
    }

    if (m_issuable_reqs.empty()) {
        return;
    }

    ReqFIFOEntry& next_req = m_issuable_reqs.front();
    // TODO: Maybe dont have these separately?
    auto type = next_req.type == InstType::WRITE ? Request::Type::Write :
                next_req.type == InstType::ROWC ? Request::Type::RowClone : Request::Type::Read; 
    Request req(next_req.addr, type, m_id, m_callback);
    req.id = s_mem_requests_issued;
    if (m_translation && !m_translation->translate(req)) {
        return;
    }

    if (m_llc->send(req)) {
        DEBUG("[DEPCORE:%d] Sent %s (%d) with address %ld\n",
            m_id,
            type == Request::Type::Read ? "READ" :
                type == Request::Type::RowClone ? "ROWC" : "WRITE",
            next_req.rob_idx,
            next_req.addr
        );
        m_issuable_reqs.pop();
        m_onflight_reqs[next_req.addr] = {m_clk, next_req.rob_idx};
        s_mem_requests_issued++;
    }
}

void DEPO3Core::receive(Request& req) {
    DEBUG("[DEPCORE:%d] Received %s request with address %ld at ROB[%d]\n",
        m_id,
        req.type_id == Request::Type::Read ? "READ" :
            req.type_id == Request::Type::RowClone ? "ROWC" : "WRITE",
        req.addr,
        m_onflight_reqs[req.addr].rob_idx
    );
    // Ignore WriteBacks
    if (m_onflight_reqs[req.addr].rob_idx < 0) {
        return;
    }
    m_rob.set_ready(m_onflight_reqs[req.addr].rob_idx);
    Clk_t depart = m_onflight_reqs[req.addr].depart;
    Clk_t arrive = m_clk;
    int req_duration = (int) (arrive - depart);

    if (!reached_expected_num_insts && depart != std::numeric_limits<Clk_t>::max()) {
        s_mem_access_cycles += req_duration;
        m_last_mem_cycle = depart;
        int lat_bucket = req_duration - (req_duration % m_lat_hist_sens);
        if (m_lat_histogram.find(lat_bucket) == m_lat_histogram.end()) {
            m_lat_histogram[lat_bucket] = 0;
        }
        m_lat_histogram[lat_bucket]++;
    }
}

void DEPO3Core::dump_latency_histogram() {
    if (m_dump_path == "") {
        return;
    }
    std::ofstream output(m_dump_path);
    for (const auto& [bucket_base, count] : m_lat_histogram) {
        output << fmt::format("{}, {}", bucket_base, count) << std::endl;
    }
    output.close();
}

}        // namespace Ramulator

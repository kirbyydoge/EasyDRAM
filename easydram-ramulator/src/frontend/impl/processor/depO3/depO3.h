#include <functional>
#include <limits>

#include "base/utils.h"
#include "frontend/frontend.h"
#include "translation/translation.h"
#include "frontend/impl/processor/depO3/depcore.h"
#include "frontend/impl/processor/depO3/depllc.h"

namespace Ramulator {

class DEPO3 final : public IFrontEnd, public Implementation {
  RAMULATOR_REGISTER_IMPLEMENTATION(IFrontEnd, DEPO3, "DEPO3", "Simple timing model OoO processor frontend.")

  private:
    ITranslation*  m_translation;

    int m_num_cores = -1;
    int m_num_blocking_cores = -1;
    std::vector<DEPO3Core*> m_cores;
    DEPO3LLC* m_llc;

    size_t m_num_expected_insts = 0;
    uint64_t m_num_max_cycles = 0;

    bool llc_serialize = false;
    std::string llc_serialization_filename;
    bool llc_deserialize = false;
    std::string llc_deserialization_filename;

  public:
    void init() override;
    void tick() override;
    void receive(Request& req);
    bool is_finished() override;
    void connect_memory_system(IMemorySystem* memory_system) override;
    int get_num_cores() override;
    DEPO3LLC* get_llc();
    std::vector<DEPO3Core*>& get_cores();
};

}        // namespace Ramulator
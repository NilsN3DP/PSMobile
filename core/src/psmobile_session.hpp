/*
 * psmobile_session.hpp - interne Session-Struktur.
 *
 * NICHT oeffentlich. Die App sieht nur psmobile_core.h. Dieser Header
 * existiert nur, weil sich die Implementierung auf mehrere
 * Uebersetzungseinheiten verteilt.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#ifndef PSMOBILE_SESSION_HPP
#define PSMOBILE_SESSION_HPP

#include "psmobile_core.h"

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "libslic3r/Model.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"

namespace Slic3r { class PresetBundle; }

void psm_emit_log(psm_log_level lvl, const std::string &msg);

struct psm_session
{
    std::string datadir;
    std::string resdir;

    Slic3r::Model                          model;
    Slic3r::DynamicPrintConfig             config;
    std::unique_ptr<Slic3r::PresetBundle>  presets;

    /* Slice-Job */
    std::unique_ptr<Slic3r::Print>   print;
    std::thread                      worker;
    std::mutex                       mtx;
    std::condition_variable          cv;
    std::atomic<int>                 state{ PSM_STATE_IDLE };
    std::atomic<bool>                cancel_requested{ false };
    psm_progress_cb                  progress_cb  = nullptr;
    void                            *progress_usr = nullptr;
    std::string                      gcode_tmp_path;
    psm_slice_stats                  stats{};

    std::string                      last_error;

    /* Schluesselliste fuer die generierte Experten-UI, einmal aufgebaut */
    std::vector<std::string>         config_keys;

    void set_error(const std::string &e)
    {
        last_error = e;
        psm_emit_log(PSM_LOG_ERROR, e);
    }

    void join_worker()
    {
        if (worker.joinable())
            worker.join();
    }

    /* Print sauber abbauen.
     *
     * Muss vor jedem Zuruecksetzen von `print` aufgerufen werden.
     * Grund: ~Print ruft ueber clear() -> invalidate_all_steps() den
     * Cancel-Callback auf. unique_ptr::reset() nullt den Zeiger aber,
     * bevor der Destruktor laeuft - ein Callback, der ueber `print`
     * zurueckgreift, laeuft dann in einen Nullzeiger. Deshalb erst die
     * Callbacks entschaerfen, dann freigeben. */
    void teardown_print();

    ~psm_session();
};

#endif /* PSMOBILE_SESSION_HPP */

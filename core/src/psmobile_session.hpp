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
#include <deque>
#include <memory>
#include <mutex>
#include <map>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "libslic3r/Model.hpp"
#include "libslic3r/Print.hpp"
#include "libslic3r/PrintConfig.hpp"

namespace Slic3r {
class PresetBundle;
struct GCodeProcessorResult;
}

void psm_emit_log(psm_log_level lvl, const std::string &msg);

struct psm_session
{
    struct BedMetadata {
        std::string name;
        bool        locked = false;
    };

    struct HistorySnapshot {
        std::vector<std::unique_ptr<Slic3r::Model>> beds;
        std::vector<BedMetadata>                    bed_metadata;
        size_t                                      active_bed = 0;
        std::string                                 label;

        HistorySnapshot(
            const std::vector<std::unique_ptr<Slic3r::Model>> &source,
            const std::vector<BedMetadata> &metadata,
            size_t active,
            std::string action)
            : bed_metadata(metadata), active_bed(active),
              label(std::move(action))
        {
            beds.reserve(source.size());
            for (const auto &bed : source)
                beds.emplace_back(std::make_unique<Slic3r::Model>(*bed));
        }

        HistorySnapshot(HistorySnapshot &&) noexcept = default;
        HistorySnapshot &operator=(HistorySnapshot &&) noexcept = default;
        HistorySnapshot(const HistorySnapshot &) = delete;
        HistorySnapshot &operator=(const HistorySnapshot &) = delete;
    };

    std::string datadir;
    std::string resdir;

    /*
     * Mobil werden Betten als getrennte Projektebenen gehalten. Dadurch
     * liegt im Viewport immer genau ein Bett im sichtbaren Koordinatenraum:
     * Der Nutzer waehlt Bett 1/2/3 explizit, statt durch eine riesige
     * Desktop-Bettlandschaft zu scrollen.
     */
    std::vector<std::unique_ptr<Slic3r::Model>> bed_models;
    std::vector<BedMetadata>                    bed_metadata;
    size_t                                 active_bed = 0;
    Slic3r::DynamicPrintConfig             config;
    std::unique_ptr<Slic3r::PresetBundle>  presets;

    /*
     * Begrenzte Projekt-Historie. Model-Kopien teilen die unveraenderlichen
     * Mesh-Puffer ueber shared_ptr; damit kostet ein Verschieben nicht noch
     * einmal die komplette STL im Speicher. Preset-Aenderungen bleiben
     * vorerst ausserhalb der Historie, Modell-, Volumen- und Bettaktionen
     * sind dagegen vollstaendig enthalten.
     */
    std::deque<HistorySnapshot>            undo_history;
    std::deque<HistorySnapshot>            redo_history;
    size_t                                 history_depth = 0;
    bool                                   history_checkpoint_taken = false;
    bool                                   defer_design_change = false;
    std::string                            history_label;
    static constexpr size_t                HISTORY_LIMIT = 30;

    /*
     * Ob auch Profile aufgelistet werden, die zum gewaehlten Drucker
     * nicht passen. PrusaSlicer nennt das "Show incompatible print and
     * filament presets" und haelt es in den Einstellungen vor.
     *
     * Mobil war die Liste anfangs immer gefiltert, weil ohne Suche
     * niemand durch achthundert Filamente scrollt. Seit die Materialwahl
     * eine Suche hat, ist die vollstaendige Liste wieder handhabbar -
     * und wer ein fremdes Filament bewusst einsetzen will, kam vorher
     * gar nicht daran.
     */
    bool                                   show_incompatible = false;

    psm_session();

    Slic3r::Model &model()
    {
        return *bed_models[active_bed];
    }

    const Slic3r::Model &model() const
    {
        return *bed_models[active_bed];
    }

    /*
     * Modell, Konfiguration und Presets werden auch vom GL-Thread
     * gelesen. Der rekursive Mutex ist noetig, weil einige Helfer
     * innerhalb einer bereits geschuetzten Operation wieder ueber das
     * C-ABI gehen (zum Beispiel Bettmodell/-textur).
     */
    std::recursive_mutex             data_mtx;

    /* Slice-Job */
    std::unique_ptr<Slic3r::Print>   print;
    std::unique_ptr<Slic3r::Model>   slice_model;
    Slic3r::DynamicPrintConfig       slice_config;
    std::thread                      worker;
    std::mutex                       mtx;
    std::mutex                       print_mtx;
    std::mutex                       result_mtx;
    std::condition_variable          cv;
    std::atomic<int>                 state{ PSM_STATE_IDLE };
    std::atomic<bool>                cancel_requested{ false };
    psm_progress_cb                  progress_cb  = nullptr;
    void                            *progress_usr = nullptr;
    std::string                      gcode_tmp_path;
    psm_slice_stats                  stats{};
    /* Verbrauch je Extruder des letzten Ergebnisses. Bei einem Extruder
     * eine Zeile, bei fuenf Farben fuenf - und dann ist die Gesamtsumme
     * in stats fast nichts wert. */
    std::vector<psm_extruder_usage>  extruder_usage;
    /*
     * Exakt der Datensatz, den Print::export_gcode finalisiert hat.
     * Der Viewport und das additive C-ABI lesen beide diese Quelle;
     * Print-Geometrie vor der G-Code-Verarbeitung ist kein Ersatz.
     */
    std::shared_ptr<const Slic3r::GCodeProcessorResult> preview_result;
    psm_preview_snapshot             preview_snapshot{};
    std::vector<psm_preview_layer>   preview_layers;
    std::vector<psm_preview_extruder> preview_extruders;
    std::vector<psm_preview_role>    preview_roles;

    /*
     * design_revision beschreibt exakt den Stand, aus dem ein Slice
     * entstehen muss. Der Worker merkt sich seine Startrevision. Nur
     * wenn sie beim Abschluss noch aktuell ist, darf sein G-Code
     * exportiert oder gesendet werden.
     */
    std::atomic<uint64_t>            design_revision{ 1 };
    std::atomic<uint64_t>            running_revision{ 0 };
    std::atomic<uint64_t>            result_revision{ 0 };

    std::string                      last_error;

    /* Ergebnis der Abhaengigkeitsregeln, zwischengespeichert.
     * config_revision zaehlt bei jeder Aenderung hoch; solange sie
     * gleich bleibt, gilt die Karte. */
    std::map<std::string, bool>      toggles;
    uint64_t                         config_revision = 0;
    uint64_t                         toggle_revision = ~0ull;

    /* Schluesselliste fuer die generierte Experten-UI, einmal aufgebaut */
    std::vector<std::string>         config_keys;

    /* Ergebnis von psm_printer_models_scan - fuer die Ersteinrichtung */
    struct ScannedModel {
        std::string vendor_id;
        std::string model_id;
        std::string name;
        std::string family;
        int         technology = 0;   /* 0 = FFF, 1 = SLA */
        std::vector<std::string> variants;
        std::string bundle_path;
    };
    std::vector<ScannedModel> printer_models;

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

    void history_begin(const std::string &label)
    {
        if (history_depth++ == 0) {
            history_checkpoint_taken = false;
            history_label = label;
        }
    }

    void history_checkpoint(const std::string &label)
    {
        if (history_depth > 0 && history_checkpoint_taken)
            return;
        undo_history.emplace_back(
            bed_models,
            bed_metadata,
            active_bed,
            history_depth > 0 && ! history_label.empty() ? history_label : label);
        while (undo_history.size() > HISTORY_LIMIT)
            undo_history.pop_front();
        redo_history.clear();
        if (history_depth > 0)
            history_checkpoint_taken = true;
    }

    void history_end()
    {
        if (history_depth == 0)
            return;
        if (--history_depth == 0) {
            history_checkpoint_taken = false;
            history_label.clear();
        }
    }

    void history_clear()
    {
        undo_history.clear();
        redo_history.clear();
        history_depth = 0;
        history_checkpoint_taken = false;
        history_label.clear();
    }

    void mark_design_changed()
    {
        design_revision.fetch_add(1, std::memory_order_acq_rel);
        result_revision.store(0, std::memory_order_release);
        {
            std::lock_guard<std::mutex> result_lock(result_mtx);
            preview_result.reset();
            preview_snapshot = psm_preview_snapshot{};
            preview_layers.clear();
            preview_extruders.clear();
            preview_roles.clear();
        }

        int expected = PSM_STATE_DONE;
        state.compare_exchange_strong(expected, PSM_STATE_STALE,
                                      std::memory_order_acq_rel);
    }

    bool result_is_current() const
    {
        const uint64_t result = result_revision.load(std::memory_order_acquire);
        return state.load(std::memory_order_acquire) == PSM_STATE_DONE &&
               result != 0 &&
               result == design_revision.load(std::memory_order_acquire);
    }

    ~psm_session();
};

#endif /* PSMOBILE_SESSION_HPP */

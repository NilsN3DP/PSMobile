///|/ PSMobile
///|/
#include "psmobile_core.h"

#include "libslic3r/miniz_extension.hpp"

#include <boost/filesystem.hpp>
#include <boost/algorithm/string.hpp>

#include <cstring>
#include <string>

namespace {

/*
 * Nur was wir ohnehin importieren koennen. Alles andere in der ZIP
 * (Vorschaubilder, Profile, Liesmich-Dateien) wird stillschweigend
 * uebergangen statt den Import mit einem Fehler abzubrechen - eine
 * ZIP von Printables enthaelt fast immer mehr als nur das Modell.
 */
bool ist_modelldatei(const std::string &name)
{
    std::string tief = name;
    boost::algorithm::to_lower(tief);
    static const char *endungen[] = {".stl", ".obj", ".3mf", ".amf"};
    for (const char *e : endungen)
        if (boost::algorithm::ends_with(tief, e))
            return true;
    return false;
}

} // namespace

/*
 * Entpackt Modelldateien aus einer ZIP in ein Zielverzeichnis, flach -
 * keine Unterordner, Namenskollisionen ueberschreiben sich schlicht.
 * Fuer den Teilen-Import: iOS liefert eine geteilte ZIP nur als Datei,
 * nie schon entpackt. Braucht keine Sitzung, ist eine reine
 * Dateioperation und laeuft deshalb vor jedem psm_session_create.
 */
PSM_API psm_result psm_zip_extract_models(const char *zip_path,
                                          const char *dest_dir,
                                          size_t *out_count)
{
    if (zip_path == nullptr || dest_dir == nullptr || out_count == nullptr)
        return PSM_ERR_INVALID_ARG;
    *out_count = 0;

    try {
        mz_zip_archive zip{};
        if (! Slic3r::open_zip_reader(&zip, zip_path))
            return PSM_ERR_GENERIC;

        struct Waechter {
            mz_zip_archive *z;
            ~Waechter() { Slic3r::close_zip_reader(z); }
        } waechter{&zip};

        boost::system::error_code ec;
        boost::filesystem::create_directories(dest_dir, ec);

        const mz_uint anzahl = mz_zip_reader_get_num_files(&zip);
        for (mz_uint i = 0; i < anzahl; ++i) {
            if (mz_zip_reader_is_file_a_directory(&zip, i))
                continue;

            char puffer[1024] = {0};
            mz_zip_reader_get_filename(&zip, i, puffer, sizeof(puffer));
            const std::string eintrag(puffer);

            // Nur der Dateiname, kein Zwischenpfad - sonst koennte ein
            // boeswillig gebautes "../../" aus dem Zielverzeichnis
            // hinausfuehren.
            const std::string basis =
                boost::filesystem::path(eintrag).filename().string();
            if (basis.empty() || ! ist_modelldatei(basis))
                continue;

            const boost::filesystem::path ziel =
                boost::filesystem::path(dest_dir) / basis;
            if (mz_zip_reader_extract_to_file(&zip, i, ziel.string().c_str(), 0))
                ++(*out_count);
        }
        return PSM_OK;
    } catch (const std::exception &) {
        return PSM_ERR_GENERIC;
    }
}

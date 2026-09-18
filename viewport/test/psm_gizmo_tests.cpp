#include "psm_gizmo_internal.hpp"
#include "psm_bed_geometry.hpp"

#include <cstdlib>
#include <iostream>
#include <string>

namespace {

void require(bool value, const std::string &message)
{
    if (! value) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(1);
    }
}

void pruefe_x_achse(const psm::Mat4 &projection, float scale_mm,
                    float innen_x, float innen_y,
                    float aussen_x, float aussen_y,
                    const std::string &fall)
{
    const auto axes = psm::gizmo_anchors(
        PSM_GIZMO_MOVE, psm::Vec3(0.f, 0.f, 0.f), scale_mm);
    require(psm::pick_anchor(axes, projection, 400, 400,
                             innen_x, innen_y, 2.f) == 0,
            fall + ": neun Pixel neben der X-Achse treffen");
    require(psm::pick_anchor(axes, projection, 400, 400,
                             aussen_x, aussen_y, 2.f) == -1,
            fall + ": siebzehn Pixel neben der X-Achse treffen nicht");
}

} // namespace

int main()
{
    const auto core_one = psm::align_bed_model(
        0.f, 250.f, 0.f, 210.f,
        -126.999f, 126.999f, -123.274f, 145.732f);
    require(std::abs(core_one.x - 125.f) < 0.01f,
            "Core One Bettmodell wird auf X zentriert");
    require(std::abs(core_one.y - 93.771f) < 0.01f,
            "Core One Bettmodell wird auf Y zentriert");

    const psm::Mat4 projection = psm::Mat4::Identity();

    /*
     * Bei 400 Pixeln Breite bildet die Identitaetsprojektion die
     * X-Achse von (200, 200) bis (310, 200) ab. Getroffen werden muss
     * die sichtbare Achse, nicht nur ihr Pfeilende.
     */
    pruefe_x_achse(projection, 0.005f,
                   255.f, 209.f, 255.f, 217.f,
                   "Identitaetsprojektion");

    /*
     * Zweifacher Zoom und 37 Grad Drehung. Der Griff bleibt 110 Pixel
     * lang, weil scale_mm entsprechend halbiert wird. Die Literale sind
     * von Hand aus cos(37°) und sin(37°) hergeleitet; der Test verwendet
     * damit nicht die Picker-Rechnung als eigene Erwartung.
     */
    psm::Mat4 gedreht_gezoomt = psm::Mat4::Identity();
    gedreht_gezoomt(0, 0) =  1.5972710f;
    gedreht_gezoomt(0, 1) = -1.2036300f;
    gedreht_gezoomt(1, 0) =  1.2036300f;
    gedreht_gezoomt(1, 1) =  1.5972710f;
    pruefe_x_achse(gedreht_gezoomt, 0.0025f,
                   249.34f, 174.08f, 254.13f, 180.51f,
                   "Zoom und 37-Grad-Drehung");

    std::cout << "PASS: psm_gizmo_tests\n";
    return 0;
}

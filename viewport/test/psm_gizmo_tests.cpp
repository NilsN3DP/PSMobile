#include "psm_gizmo_internal.hpp"

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

} // namespace

int main()
{
    const psm::Mat4 projection = psm::Mat4::Identity();
    const psm::Vec3 origin(0.f, 0.f, 0.f);
    const auto axes = psm::gizmo_anchors(PSM_GIZMO_MOVE, origin, 0.005f);

    /*
     * Bei 400 Pixeln Breite bildet die Identitaetsprojektion die
     * X-Achse von (200, 200) bis (310, 200) ab. Getroffen werden muss
     * die sichtbare Achse, nicht nur ihr Pfeilende.
     */
    require(psm::pick_anchor(axes, projection, 400, 400,
                             255.f, 209.f, 2.f) == 0,
            "die X-Achse hat eine pixelbasierte Mindest-Trefferhuelle");
    require(psm::pick_anchor(axes, projection, 400, 400,
                             255.f, 217.f, 2.f) == -1,
            "ausserhalb der Pixelhuelle wird keine Achse geraten");

    std::cout << "PASS: psm_gizmo_tests\n";
    return 0;
}

/*
 * psm_gizmo.cpp - Griffe am ausgewaehlten Objekt.
 *
 * Pfeile zum Verschieben, Kreise zum Drehen, Wuerfel zum Skalieren.
 *
 * PrusaSlicers Gizmo-Klassen haengen an wxWidgets und an dessen eigenem
 * Auswahl- und Raycaster-Geruest; uebernehmen laesst sich davon nichts.
 * Aussehen und Bedienlogik folgen dem Original - Achsenfarben rot,
 * gruen, blau, Rastung beim langsamen Ziehen -, der Code ist neu. Das
 * ist einer der wenigen Punkte, an denen E-12 ausdruecklich den Nachbau
 * vorsieht.
 *
 * Zwei Entscheidungen, die vom Desktop abweichen und Absicht sind:
 *
 *   - Die Griffe haben feste Bildschirmgroesse. Am Desktop skalieren sie
 *     mit der Szene; auf einem Tablet waeren sie beim Herauszoomen nicht
 *     mehr zu treffen.
 *   - Die Treffererkennung laeuft ueber die auf den Bildschirm
 *     projizierten Ankerpunkte, nicht ueber GL-Picking. Das braucht
 *     keinen zweiten Zeichendurchgang und keinen Lesezugriff auf den
 *     Bildpuffer - beides auf mobilen Kernen teuer.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psm_gizmo_internal.hpp"

#include <algorithm>
#include <cmath>

namespace psm {

namespace {

/* Laenge der Griffe in Bildpunkten - so gross wie ein Fingerziel. */
constexpr float HANDLE_PX = 110.f;
constexpr float TWO_PI    = 6.28318530718f;

} // namespace

bool project_point(const Mat4 &view_proj, const Vec3 &p, int w, int h, Vec2 &out)
{
    const Eigen::Vector4f clip =
        view_proj * Eigen::Vector4f(p.x(), p.y(), p.z(), 1.f);
    if (clip.w() <= 0.f)
        return false;                       /* hinter der Kamera */
    out.x() = (clip.x() / clip.w() * 0.5f + 0.5f) * static_cast<float>(w);
    out.y() = (1.f - (clip.y() / clip.w() * 0.5f + 0.5f)) * static_cast<float>(h);
    return true;
}

namespace {

/*
 * Wie viele Weltmillimeter entsprechen einem Bildpunkt am Ort des
 * Objekts? Damit bekommen die Griffe ihre feste Bildschirmgroesse.
 */
float mm_per_pixel(const Mat4 &view_proj, const Vec3 &origin, int w, int h)
{
    Vec2 a, b;
    if (! project_point(view_proj, origin, w, h, a) ||
        ! project_point(view_proj, origin + Vec3(10.f, 0.f, 0.f), w, h, b))
        return 1.f;
    const float px = (b - a).norm();
    return px > 0.001f ? 10.f / px : 1.f;
}

} // namespace

Vec3 axis_vector(int axis)
{
    switch (axis) {
        case 0:  return Vec3(1.f, 0.f, 0.f);
        case 1:  return Vec3(0.f, 1.f, 0.f);
        default: return Vec3(0.f, 0.f, 1.f);
    }
}

void axis_color(int axis, bool active, float out[4])
{
    /* Dieselbe Zuordnung wie im Original: X rot, Y gruen, Z blau. */
    static const float base[3][3] = {
        { 0.85f, 0.20f, 0.20f },
        { 0.20f, 0.75f, 0.25f },
        { 0.25f, 0.45f, 0.90f },
    };
    const int i = std::clamp(axis, 0, 2);
    const float lift = active ? 0.35f : 0.f;
    for (int c = 0; c < 3; ++c)
        out[c] = std::min(1.f, base[i][c] + lift);
    out[3] = 1.f;
}

/* ------------------------------------------------------------------ */
/* Geometrie                                                           */
/* ------------------------------------------------------------------ */

void build_arrow(std::vector<Vertex> &out, const Vec3 &origin,
                 const Vec3 &dir, float length)
{
    /* Schaft als Linie, Spitze als vier Dreiecke - reicht voellig, die
     * Griffe sind nur ein paar Dutzend Bildpunkte gross. */
    const Vec3 tip = origin + dir * length;
    const Vec3 base = origin + dir * (length * 0.78f);

    out.push_back({ origin.x(), origin.y(), origin.z(), 0.f, 0.f, 1.f });
    out.push_back({ base.x(),   base.y(),   base.z(),   0.f, 0.f, 1.f });

    /* Zwei zur Achse senkrechte Richtungen fuer die Spitze. */
    Vec3 up = std::abs(dir.z()) > 0.9f ? Vec3(1.f, 0.f, 0.f) : Vec3(0.f, 0.f, 1.f);
    const Vec3 s1 = dir.cross(up).normalized();
    const Vec3 s2 = dir.cross(s1).normalized();
    const float r = length * 0.07f;

    const Vec3 ring[4] = { base + s1 * r, base + s2 * r,
                           base - s1 * r, base - s2 * r };
    for (int i = 0; i < 4; ++i) {
        const Vec3 &a = ring[i];
        const Vec3 &b = ring[(i + 1) % 4];
        out.push_back({ tip.x(), tip.y(), tip.z(), 0.f, 0.f, 1.f });
        out.push_back({ a.x(), a.y(), a.z(), 0.f, 0.f, 1.f });
        out.push_back({ b.x(), b.y(), b.z(), 0.f, 0.f, 1.f });
    }
}

void build_circle(std::vector<Vertex> &out, const Vec3 &origin,
                  int axis, float radius, int segments)
{
    /* Zwei zur Achse senkrechte Richtungen spannen die Kreisebene auf. */
    const Vec3 n = axis_vector(axis);
    Vec3 up = std::abs(n.z()) > 0.9f ? Vec3(1.f, 0.f, 0.f) : Vec3(0.f, 0.f, 1.f);
    const Vec3 e1 = n.cross(up).normalized();
    const Vec3 e2 = n.cross(e1).normalized();

    Vec3 prev = origin + e1 * radius;
    for (int i = 1; i <= segments; ++i) {
        const float a = TWO_PI * static_cast<float>(i) / static_cast<float>(segments);
        const Vec3 p = origin + e1 * (std::cos(a) * radius) + e2 * (std::sin(a) * radius);
        out.push_back({ prev.x(), prev.y(), prev.z(), 0.f, 0.f, 1.f });
        out.push_back({ p.x(), p.y(), p.z(), 0.f, 0.f, 1.f });
        prev = p;
    }
}

void build_box(std::vector<Vertex> &out, const Vec3 &center, float half)
{
    const float h = half;
    /* Zwoelf Dreiecke, Normalen zeigen nach aussen. Der flache Shader
     * benutzt sie nicht, aber sie kosten nichts und halten den
     * Eckpunkttyp einheitlich. */
    static const int faces[6][4] = {
        { 0, 1, 3, 2 }, { 4, 6, 7, 5 }, { 0, 4, 5, 1 },
        { 2, 3, 7, 6 }, { 0, 2, 6, 4 }, { 1, 5, 7, 3 },
    };
    Vec3 c[8];
    for (int i = 0; i < 8; ++i)
        c[i] = center + Vec3((i & 1) ? h : -h, (i & 2) ? h : -h, (i & 4) ? h : -h);

    for (const auto &f : faces) {
        const Vec3 &a = c[f[0]], &b = c[f[1]], &d = c[f[2]], &e = c[f[3]];
        const Vec3 nrm = (b - a).cross(d - a).normalized();
        for (const Vec3 &p : { a, b, d, a, d, e })
            out.push_back({ p.x(), p.y(), p.z(), nrm.x(), nrm.y(), nrm.z() });
    }
}

/* ------------------------------------------------------------------ */
/* Ankerpunkte fuer die Treffererkennung                               */
/* ------------------------------------------------------------------ */

std::vector<Anchor> gizmo_anchors(psm_gizmo_mode mode, const Vec3 &origin,
                                  float scale_mm)
{
    std::vector<Anchor> out;
    const float len = HANDLE_PX * scale_mm;

    switch (mode) {
        case PSM_GIZMO_MOVE:
        case PSM_GIZMO_SCALE:
            /* Der Griff sitzt am Ende der Achse. */
            for (int a = 0; a < 3; ++a)
                out.push_back({ a, origin + axis_vector(a) * len });
            if (mode == PSM_GIZMO_SCALE) {
                /* Gleichmaessig: der Griff auf der Winkelhalbierenden. */
                const Vec3 d = Vec3(1.f, 1.f, 1.f).normalized();
                out.push_back({ 3, origin + d * (len * 0.75f) });
            }
            break;

        case PSM_GIZMO_ROTATE:
            /*
             * Beim Kreis reicht ein Punkt nicht - er laege je nach
             * Blickwinkel hinter dem Objekt. Deshalb vier Punkte je
             * Kreis; der naechste entscheidet.
             */
            for (int a = 0; a < 3; ++a) {
                const Vec3 n = axis_vector(a);
                Vec3 up = std::abs(n.z()) > 0.9f ? Vec3(1.f, 0.f, 0.f)
                                                 : Vec3(0.f, 0.f, 1.f);
                const Vec3 e1 = n.cross(up).normalized();
                const Vec3 e2 = n.cross(e1).normalized();
                for (int k = 0; k < 4; ++k) {
                    const float ang = TWO_PI * static_cast<float>(k) / 4.f;
                    out.push_back({ a, origin + e1 * (std::cos(ang) * len)
                                             + e2 * (std::sin(ang) * len) });
                }
            }
            break;

        default:
            break;
    }
    return out;
}

int pick_anchor(const std::vector<Anchor> &anchors, const Mat4 &view_proj,
                int w, int h, float x, float y, float radius_px)
{
    int   best = -1;
    float best_d = radius_px;

    for (const Anchor &a : anchors) {
        Vec2 s;
        if (! project_point(view_proj, a.world, w, h, s))
            continue;
        const float d = std::hypot(s.x() - x, s.y() - y);
        if (d < best_d) {
            best_d = d;
            best   = a.axis;
        }
    }
    return best;
}

float screen_scale(const Mat4 &view_proj, const Vec3 &origin, int w, int h)
{
    return mm_per_pixel(view_proj, origin, w, h);
}

float handle_length_px() { return HANDLE_PX; }

} // namespace psm

/*
 * psm_gizmo_internal.hpp - gemeinsame Typen fuer die Griffe.
 *
 * Bewusst getrennt von psm_viewport.cpp: die Geometrie und die
 * Treffererkennung der Griffe haengen an keinem GL-Zustand und lassen
 * sich so ohne Kontext pruefen.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#ifndef PSM_GIZMO_INTERNAL_HPP
#define PSM_GIZMO_INTERNAL_HPP

#include <vector>

#include <Eigen/Dense>

#include "psm_viewport.h"

namespace psm {

using Vec2 = Eigen::Vector2f;
using Vec3 = Eigen::Vector3f;
using Mat4 = Eigen::Matrix4f;

/* Muss zum Eckpunkttyp in psm_viewport.cpp passen. */
struct Vertex { float px, py, pz, nx, ny, nz; };

/** Eine in Weltkoordinaten beschriebene Griff-Trefferzone. */
struct Anchor {
    int  axis;     /* 0 = X, 1 = Y, 2 = Z, 3 = gleichmaessig */
    Vec3 from;
    Vec3 to;
};

/** Weltpunkt auf Bildschirmkoordinaten. False, wenn hinter der Kamera. */
bool project_point(const Mat4 &view_proj, const Vec3 &p, int w, int h, Vec2 &out);

Vec3 axis_vector(int axis);
void axis_color(int axis, bool active, float out[4]);

void build_arrow(std::vector<Vertex> &out, const Vec3 &origin,
                 const Vec3 &dir, float length);
/**
 * Kreis als Band, das immer zur Kamera zeigt.
 *
 * Eine Linie ist auf einem 2560 Punkte breiten Schirm ein Haar - man
 * sieht sie kaum und trifft sie nicht. Deshalb wird jedes Segment zu
 * zwei Dreiecken aufgezogen, deren Breite senkrecht zur Blickrichtung
 * steht. Dieselbe Technik, mit der man ueberall dicke Linien zeichnet.
 *
 * @param to_camera Richtung vom Kreis zur Kamera, normiert
 */
void build_ring_band(std::vector<Vertex> &out, const Vec3 &origin,
                     int axis, float radius, float width,
                     const Vec3 &to_camera, int segments);

/** Gerades Band von a nach b, ebenfalls zur Kamera gedreht. */
void build_line_band(std::vector<Vertex> &out, const Vec3 &a, const Vec3 &b,
                     float width, const Vec3 &to_camera);

/** Kleine Kugel als sichtbarer Anfasspunkt. */
void build_ball(std::vector<Vertex> &out, const Vec3 &center, float radius);

void build_circle(std::vector<Vertex> &out, const Vec3 &origin,
                  int axis, float radius, int segments);
void build_box(std::vector<Vertex> &out, const Vec3 &center, float half);

std::vector<Anchor> gizmo_anchors(psm_gizmo_mode mode, const Vec3 &origin,
                                  float scale_mm);

int pick_anchor(const std::vector<Anchor> &anchors, const Mat4 &view_proj,
                int w, int h, float x, float y, float radius_px);

/** Weltmillimeter je Bildpunkt am Ort des Objekts. */
float screen_scale(const Mat4 &view_proj, const Vec3 &origin, int w, int h);

/** Laenge der Griffe in Bildpunkten. */
float handle_length_px();

} // namespace psm

#endif /* PSM_GIZMO_INTERNAL_HPP */

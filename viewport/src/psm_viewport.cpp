/*
 * psm_viewport.cpp - OpenGL-ES-Viewport.
 *
 * Nutzt die Shader aus PrusaSlicer (resources/shaders/ES) unveraendert.
 * Deren Konvention gibt die Namen vor:
 *   attribute  v_position, v_normal
 *   uniform    view_model_matrix, projection_matrix, view_normal_matrix,
 *              uniform_color
 *
 * Es wird bewusst GLES 2.0 / GLSL ES 1.00 angesprochen, weil genau dafuer
 * die vorhandenen Shader geschrieben sind. Das laeuft auf jedem Geraet,
 * das die App ueberhaupt erreicht.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include "psm_viewport.h"
#include "psm_gizmo_internal.hpp"
#include "psmobile_session.hpp"

#include <GLES2/gl2.h>

#include <algorithm>
#include <cmath>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include <Eigen/Geometry>

#include "libslic3r/Model.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "libslic3r/TriangleMesh.hpp"

/* NanoSVG rastert die Bett-Textur - dieselbe Bibliothek, die
 * PrusaSlicer in GLTexture.cpp dafuer benutzt. */
#include <nanosvg/nanosvg.h>
#include <nanosvg/nanosvgrast.h>

/* Der Vorschau-Renderer aus PrusaSlicer, unveraendert. */
#include <Viewer.hpp>
#include "slic3r/GUI/LibVGCode/LibVGCodeWrapper.hpp"

namespace {

using Mat4 = Eigen::Matrix4f;
using Vec3 = Eigen::Vector3f;

constexpr float PI_F = 3.14159265358979323846f;

/* ------------------------------------------------------------------ */
/* Shader                                                              */
/* ------------------------------------------------------------------ */

struct Program {
    GLuint id = 0;
    GLint  u_view_model = -1;
    GLint  u_projection = -1;
    GLint  u_normal     = -1;
    GLint  u_color      = -1;
    GLint  a_position   = -1;
    GLint  a_normal     = -1;
    /* Nur der printbed-Shader: die Textur des Druckbereichs. */
    GLint  a_tex_coord   = -1;
    GLint  u_texture     = -1;
    GLint  u_transparent = -1;
    GLint  u_svg_source  = -1;

    void use() const { glUseProgram(id); }

    void destroy()
    {
        if (id != 0) { glDeleteProgram(id); id = 0; }
    }
};

std::string read_file(const std::string &path)
{
    std::ifstream f(path, std::ios::binary);
    if (! f)
        return {};
    std::ostringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

GLuint compile(GLenum type, const std::string &src, std::string &err)
{
    const GLuint sh = glCreateShader(type);
    const char *p = src.c_str();
    glShaderSource(sh, 1, &p, nullptr);
    glCompileShader(sh);

    GLint ok = GL_FALSE;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (ok != GL_TRUE) {
        GLint len = 0;
        glGetShaderiv(sh, GL_INFO_LOG_LENGTH, &len);
        std::string log(static_cast<size_t>(std::max(len, 1)), '\0');
        glGetShaderInfoLog(sh, len, nullptr, log.data());
        err = log;
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

bool link_program(Program &prg, const std::string &dir, const std::string &name, std::string &err)
{
    const std::string vs_src = read_file(dir + "/" + name + ".vs");
    const std::string fs_src = read_file(dir + "/" + name + ".fs");
    if (vs_src.empty() || fs_src.empty()) {
        err = "Shader nicht gefunden: " + dir + "/" + name;
        return false;
    }

    const GLuint vs = compile(GL_VERTEX_SHADER, vs_src, err);
    if (vs == 0) { err = name + ".vs: " + err; return false; }
    const GLuint fs = compile(GL_FRAGMENT_SHADER, fs_src, err);
    if (fs == 0) { err = name + ".fs: " + err; glDeleteShader(vs); return false; }

    prg.id = glCreateProgram();
    glAttachShader(prg.id, vs);
    glAttachShader(prg.id, fs);
    glLinkProgram(prg.id);
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint ok = GL_FALSE;
    glGetProgramiv(prg.id, GL_LINK_STATUS, &ok);
    if (ok != GL_TRUE) {
        GLint len = 0;
        glGetProgramiv(prg.id, GL_INFO_LOG_LENGTH, &len);
        std::string log(static_cast<size_t>(std::max(len, 1)), '\0');
        glGetProgramInfoLog(prg.id, len, nullptr, log.data());
        err = name + ": " + log;
        prg.destroy();
        return false;
    }

    prg.u_view_model = glGetUniformLocation(prg.id, "view_model_matrix");
    prg.u_projection = glGetUniformLocation(prg.id, "projection_matrix");
    prg.u_normal     = glGetUniformLocation(prg.id, "view_normal_matrix");
    prg.u_color      = glGetUniformLocation(prg.id, "uniform_color");
    prg.a_position   = glGetAttribLocation(prg.id, "v_position");
    prg.a_normal     = glGetAttribLocation(prg.id, "v_normal");
    prg.a_tex_coord   = glGetAttribLocation(prg.id, "v_tex_coord");
    prg.u_texture     = glGetUniformLocation(prg.id, "texture");
    prg.u_transparent = glGetUniformLocation(prg.id, "transparent_background");
    prg.u_svg_source  = glGetUniformLocation(prg.id, "svg_source");
    return true;
}

/* ------------------------------------------------------------------ */
/* Matrizen                                                            */
/* ------------------------------------------------------------------ */

Mat4 perspective(float fov_rad, float aspect, float znear, float zfar)
{
    Mat4 m = Mat4::Zero();
    const float t = 1.0f / std::tan(fov_rad * 0.5f);
    m(0, 0) = t / std::max(aspect, 1e-4f);
    m(1, 1) = t;
    m(2, 2) = (zfar + znear) / (znear - zfar);
    m(2, 3) = (2.0f * zfar * znear) / (znear - zfar);
    m(3, 2) = -1.0f;
    return m;
}

Mat4 look_at(const Vec3 &eye, const Vec3 &center, const Vec3 &up)
{
    const Vec3 f = (center - eye).normalized();
    const Vec3 s = f.cross(up).normalized();
    const Vec3 u = s.cross(f);

    Mat4 m = Mat4::Identity();
    m.block<1, 3>(0, 0) =  s.transpose();
    m.block<1, 3>(1, 0) =  u.transpose();
    m.block<1, 3>(2, 0) = -f.transpose();
    m(0, 3) = -s.dot(eye);
    m(1, 3) = -u.dot(eye);
    m(2, 3) =  f.dot(eye);
    return m;
}

/* ------------------------------------------------------------------ */
/* Geometriepuffer                                                     */
/* ------------------------------------------------------------------ */

struct Mesh {
    GLuint vbo = 0;
    GLsizei vertex_count = 0;
    psm_object_id owner = PSM_INVALID_ID;
    Slic3r::BoundingBoxf3 bbox;

    void destroy()
    {
        if (vbo != 0) { glDeleteBuffers(1, &vbo); vbo = 0; }
        vertex_count = 0;
    }
};

/* Position und Normale verschraenkt - ein Puffer, ein Bindevorgang. */
struct Vertex { float px, py, pz, nx, ny, nz; };

} // namespace

/* ------------------------------------------------------------------ */

struct psm_viewport
{
    psm_session *session = nullptr;
    std::string  shader_dir;
    Mesh         bed_model;      /* Prusas STL, falls vorhanden */
    bool         has_bed_model = false;
    GLuint       bed_tex = 0;    /* gerasterte SVG des Druckbereichs */
    bool         has_bed_texture = false;
    std::string  last_error;

    Program prog_lit;    // gouraud_light - Modelle
    Program prog_flat;   // flat          - Bett und Raster
    Program prog_bed;    // printbed      - Bettflaeche mit Textur

    std::vector<Mesh> meshes;
    Mesh bed_fill;
    Mesh bed_grid;

    /* Griffe am ausgewaehlten Objekt */
    psm_gizmo_mode gizmo = PSM_GIZMO_NONE;
    bool  gizmo_dirty = true;
    int   gizmo_hover = -1;          /* gerade angefasster Griff */
    Mesh  gizmo_lines;               /* Schaefte und Kreise */
    Mesh  gizmo_solid[4];            /* Spitzen bzw. Wuerfel je Achse */
    int   gizmo_axis_count = 0;

    bool  dirty = true;
    int   width = 1, height = 1;

    /* Kamera */
    Vec3  target   { 0.f, 0.f, 0.f };
    float distance = 300.f;
    float yaw      = -0.6f;
    float pitch    =  0.55f;

    Slic3r::BoundingBoxf3 scene_bbox;
    psm_object_id selection = PSM_INVALID_ID;

    /* Vorschau */
    psm_view_mode           mode = PSM_VIEW_EDITOR;
    libvgcode::Viewer       gcode_viewer;
    bool                    gcode_viewer_ready = false;
    bool                    gcode_loaded = false;

    Vec3 eye() const
    {
        /*
         * Bei yaw = 0 steht die Kamera VOR dem Bett, also bei -Y, und
         * blickt nach +Y. Das Vorzeichen war lange falsch herum: "Vorn"
         * zeigte die Rueckseite, "Hinten" die Vorderseite, und in der
         * Draufsicht lag die Y-Achse gespiegelt - weshalb Bettmodell und
         * Aufschrift verdreht wirkten.
         */
        return target + Vec3(
             distance * std::cos(pitch) * std::sin(yaw),
            -distance * std::cos(pitch) * std::cos(yaw),
             distance * std::sin(pitch));
    }

    Mat4 view() const { return look_at(eye(), target, Vec3(0.f, 0.f, 1.f)); }

    Mat4 projection() const
    {
        const float aspect = static_cast<float>(width) / static_cast<float>(std::max(height, 1));
        const float far_plane = std::max(distance * 4.f, 1000.f);
        return perspective(45.f * PI_F / 180.f, aspect, 0.5f, far_plane);
    }
};

namespace {

void upload(Mesh &m, const std::vector<Vertex> &verts)
{
    m.destroy();
    if (verts.empty())
        return;
    glGenBuffers(1, &m.vbo);
    glBindBuffer(GL_ARRAY_BUFFER, m.vbo);
    glBufferData(GL_ARRAY_BUFFER,
                 static_cast<GLsizeiptr>(verts.size() * sizeof(Vertex)),
                 verts.data(), GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    m.vertex_count = static_cast<GLsizei>(verts.size());
}

/** Baut das Druckbett aus der aktiven Konfiguration - nicht geraten. */
/*
 * Prusas eigenes Bettmodell laden.
 *
 * Zu jedem Druckermodell liefert Prusa ein STL des Betts mit; der Name
 * steht im Herstellerbuendel und kommt ueber psm_bed_model_file. Das
 * flache Vieleck aus bed_shape bleibt als Rueckfallebene fuer Drucker
 * ohne Modell und fuer eigene Bettformen.
 *
 * @return true wenn ein Modell geladen wurde
 */
bool build_bed_model(psm_viewport *v)
{
    char path_buf[512] = { 0 };
    if (psm_bed_model_file(v->session, path_buf, sizeof(path_buf)) != PSM_OK ||
        path_buf[0] == 0)
        return false;

    /* Der Pfad ist bereits vollstaendig: system_printer_bed_model setzt
     * ihn aus data_dir bzw. resources_dir zusammen, und beide haben wir
     * beim Anlegen der Sitzung gesetzt. */
    const std::string path = path_buf;

    Slic3r::TriangleMesh mesh;
    try {
        if (! mesh.ReadSTLFile(path.c_str()) || mesh.empty()) {
            psm_emit_log(PSM_LOG_WARN, "Bettmodell nicht lesbar: " + path);
            return false;
        }
    } catch (const std::exception &e) {
        psm_emit_log(PSM_LOG_WARN, std::string("Bettmodell: ") + e.what());
        return false;
    }

    /*
     * Das STL ist um seinen eigenen Ursprung modelliert. PrusaSlicer legt
     * es in Bed3D::init_internal_model_from_file so ab, dass der Ursprung
     * in die Mitte der Bettflaeche faellt, und schiebt es 0,03 mm nach
     * unten, damit es nicht mit der Textur um dieselben Pixel streitet.
     */
    float cx = 0.f, cy = 0.f;
    try {
        const Slic3r::Points pts = Slic3r::get_bed_shape(v->session->config);
        if (pts.size() >= 3) {
            Slic3r::BoundingBoxf bb;
            for (const Slic3r::Point &p : pts)
                bb.merge(Slic3r::Vec2d(Slic3r::unscale<double>(p.x()),
                                       Slic3r::unscale<double>(p.y())));
            cx = static_cast<float>((bb.min.x() + bb.max.x()) * 0.5);
            cy = static_cast<float>((bb.min.y() + bb.max.y()) * 0.5);
        }
    } catch (...) {
        /* Ohne Bettform bleibt es beim Ursprung. */
    }
    const float cz = -0.03f;

    std::vector<Vertex> verts;
    verts.reserve(mesh.its.indices.size() * 3);
    for (const Slic3r::Vec3i32 &tri : mesh.its.indices) {
        const Slic3r::Vec3f &a = mesh.its.vertices[tri(0)];
        const Slic3r::Vec3f &b = mesh.its.vertices[tri(1)];
        const Slic3r::Vec3f &c = mesh.its.vertices[tri(2)];
        const Slic3r::Vec3f n = (b - a).cross(c - a).normalized();
        for (const Slic3r::Vec3f &p : { a, b, c })
            verts.push_back({ p.x() + cx, p.y() + cy, p.z() + cz,
                              n.x(), n.y(), n.z() });
    }

    upload(v->bed_model, verts);
    psm_emit_log(PSM_LOG_INFO,
                 "Bettmodell geladen: " + path + " (" +
                 std::to_string(mesh.its.indices.size()) + " Dreiecke)");
    return true;
}

/*
 * Textur des Druckbereichs.
 *
 * Prusa legt zu jedem Drucker eine SVG bei (mk4s.svg, xl.svg ...). Sie
 * wird mit NanoSVG gerastert - derselbe Weg wie in GLTexture.cpp - und
 * als Textur auf die Bettflaeche gelegt. Der Shader printbed.fs mischt
 * sie mit seinem eigenen Farbverlauf, deshalb reicht der Alphakanal.
 *
 * Aufgeloest wird auf 1024 Punkte in der laengeren Kante: darunter
 * franst die Beschriftung auf dem Bett aus, darueber bringt es auf einem
 * Tablet nichts mehr.
 */
bool build_bed_texture(psm_viewport *v, const Slic3r::BoundingBoxf &bb)
{
    char path_buf[512] = { 0 };
    if (psm_bed_texture_file(v->session, path_buf, sizeof(path_buf)) != PSM_OK ||
        path_buf[0] == 0)
        return false;

    NSVGimage *img = nsvgParseFromFile(path_buf, "px", 96.0f);
    if (img == nullptr || img->width <= 0.f || img->height <= 0.f) {
        if (img != nullptr) nsvgDelete(img);
        psm_emit_log(PSM_LOG_WARN, std::string("Bett-Textur nicht lesbar: ") + path_buf);
        return false;
    }

    const int   longest = 1024;
    const float scale   = static_cast<float>(longest) /
                          std::max(img->width, img->height);
    const int   tw = std::max(1, static_cast<int>(img->width  * scale));
    const int   th = std::max(1, static_cast<int>(img->height * scale));

    std::vector<unsigned char> pixels(static_cast<size_t>(tw) * th * 4, 0);
    NSVGrasterizer *rast = nsvgCreateRasterizer();
    if (rast == nullptr) {
        nsvgDelete(img);
        return false;
    }
    nsvgRasterize(rast, img, 0.f, 0.f, scale, pixels.data(), tw, th, tw * 4);
    nsvgDeleteRasterizer(rast);
    nsvgDelete(img);

    if (v->bed_tex == 0)
        glGenTextures(1, &v->bed_tex);
    glBindTexture(GL_TEXTURE_2D, v->bed_tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tw, th, 0, GL_RGBA,
                 GL_UNSIGNED_BYTE, pixels.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);

    psm_emit_log(PSM_LOG_INFO,
                 std::string("Bett-Textur geladen: ") + path_buf + " (" +
                 std::to_string(tw) + "x" + std::to_string(th) + ")");
    return true;
}

void build_bed(psm_viewport *v)
{
    /* Erst das echte Modell, dann die texturierte Flaeche darueber. */
    v->bed_model.destroy();
    v->has_bed_model = build_bed_model(v);

    Slic3r::Points pts;
    try {
        pts = Slic3r::get_bed_shape(v->session->config);
    } catch (...) {
        pts.clear();
    }
    if (pts.size() < 3)
        return;

    Slic3r::BoundingBoxf bb;
    std::vector<Eigen::Vector2f> poly;
    poly.reserve(pts.size());
    for (const Slic3r::Point &p : pts) {
        const double x = Slic3r::unscale<double>(p.x());
        const double y = Slic3r::unscale<double>(p.y());
        poly.emplace_back(static_cast<float>(x), static_cast<float>(y));
        bb.merge(Slic3r::Vec2d(x, y));
    }

    /* Flaeche als Faecher ab dem ersten Punkt. Fuer die rechteckigen und
     * konvexen Betten der Prusa-Drucker ist das ausreichend.
     *
     * In den beiden ersten Normalenfeldern stehen die Texturkoordinaten:
     * der printbed-Shader kennt kein v_normal, dafuer ein v_tex_coord an
     * genau dieser Stelle im Puffer. So braucht es keinen zweiten
     * Eckpunkttyp.
     *
     * Die Zuordnung ist die aus Bed3D::init_triangles - (p - min) durch
     * die Groesse, mit gespiegeltem V, weil Bilder von oben nach unten
     * laufen und das Bett von unten nach oben.
     *
     * Hier stand zwischenzeitlich ein gespiegeltes U, weil die
     * Aufschrift verdreht erschien. Das war Symptombekaempfung: in
     * Wahrheit stand die Kamera falsch herum (siehe eye()). Seit das
     * behoben ist, stimmt wieder die Zuordnung des Originals.
     */
    const float bw = static_cast<float>(bb.size().x());
    const float bh = static_cast<float>(bb.size().y());
    const auto uv = [&](const Eigen::Vector2f &p) {
        return Eigen::Vector2f(
            bw > 0.f ? (p.x() - static_cast<float>(bb.min.x())) / bw : 0.f,
            bh > 0.f ? 1.f - (p.y() - static_cast<float>(bb.min.y())) / bh : 0.f);
    };

    std::vector<Vertex> fill;
    for (size_t i = 1; i + 1 < poly.size(); ++i) {
        for (const Eigen::Vector2f &p : { poly[0], poly[i], poly[i + 1] }) {
            const Eigen::Vector2f t = uv(p);
            fill.push_back({ p.x(), p.y(), 0.f, t.x(), t.y(), 0.f });
        }
    }
    upload(v->bed_fill, fill);

    v->has_bed_texture = build_bed_texture(v, bb);

    /* Raster im 10-mm-Abstand, wie im Slicer. */
    std::vector<Vertex> grid;
    const auto minp = bb.min;
    const auto maxp = bb.max;
    const float z = 0.05f;   // minimal ueber dem Bett, sonst Z-Fighting
    for (double x = std::ceil(minp.x() / 10.0) * 10.0; x <= maxp.x(); x += 10.0) {
        grid.push_back({ (float) x, (float) minp.y(), z, 0.f, 0.f, 1.f });
        grid.push_back({ (float) x, (float) maxp.y(), z, 0.f, 0.f, 1.f });
    }
    for (double y = std::ceil(minp.y() / 10.0) * 10.0; y <= maxp.y(); y += 10.0) {
        grid.push_back({ (float) minp.x(), (float) y, z, 0.f, 0.f, 1.f });
        grid.push_back({ (float) maxp.x(), (float) y, z, 0.f, 0.f, 1.f });
    }
    upload(v->bed_grid, grid);

    v->target = Vec3(static_cast<float>(bb.center().x()),
                     static_cast<float>(bb.center().y()), 0.f);
    v->distance = static_cast<float>(std::max(bb.size().x(), bb.size().y())) * 1.6f;
}

/** Erzeugt je Instanz einen Puffer aus den Dreiecken des Modells. */
void build_meshes(psm_viewport *v)
{
    for (Mesh &m : v->meshes)
        m.destroy();
    v->meshes.clear();
    v->scene_bbox = Slic3r::BoundingBoxf3();

    for (const Slic3r::ModelObject *obj : v->session->model.objects) {
        for (size_t inst = 0; inst < obj->instances.size(); ++inst) {
            const Slic3r::Transform3d inst_m = obj->instances[inst]->get_matrix();

            std::vector<Vertex> verts;
            Slic3r::BoundingBoxf3 bbox;

            for (const Slic3r::ModelVolume *vol : obj->volumes) {
                if (! vol->is_model_part())
                    continue;
                /* indexed_triangle_set kommt aus admesh und liegt im
                 * globalen Namensraum, nicht in Slic3r. */
                const indexed_triangle_set &its = vol->mesh().its;
                const Slic3r::Transform3d m = inst_m * vol->get_matrix();

                verts.reserve(verts.size() + its.indices.size() * 3);
                for (const Slic3r::Vec3i32 &tri : its.indices) {
                    Slic3r::Vec3d p[3];
                    for (int k = 0; k < 3; ++k)
                        p[k] = m * its.vertices[tri[k]].cast<double>();

                    /* Flache Normale je Dreieck: das entspricht dem, was
                     * PrusaSlicer fuer unstrukturierte Meshes auch tut,
                     * und braucht keine Nachbarschaftsinformation. */
                    const Slic3r::Vec3d n = (p[1] - p[0]).cross(p[2] - p[0]).normalized();
                    for (int k = 0; k < 3; ++k) {
                        verts.push_back({
                            (float) p[k].x(), (float) p[k].y(), (float) p[k].z(),
                            (float) n.x(),    (float) n.y(),    (float) n.z() });
                        bbox.merge(p[k]);
                    }
                }
            }

            if (verts.empty())
                continue;

            Mesh mesh;
            upload(mesh, verts);
            mesh.owner = static_cast<psm_object_id>(obj->id().id);
            mesh.bbox  = bbox;
            v->meshes.push_back(mesh);
            v->scene_bbox.merge(bbox);
        }
    }
}

void draw(const Program &p, const Mesh &m, GLenum mode,
          const Mat4 &view_model, const Mat4 &proj, const float rgba[4])
{
    if (m.vertex_count == 0)
        return;

    p.use();
    glUniformMatrix4fv(p.u_view_model, 1, GL_FALSE, view_model.data());
    glUniformMatrix4fv(p.u_projection, 1, GL_FALSE, proj.data());
    if (p.u_color >= 0)
        glUniform4fv(p.u_color, 1, rgba);
    if (p.u_normal >= 0) {
        const Eigen::Matrix3f nm = view_model.block<3, 3>(0, 0).inverse().transpose();
        glUniformMatrix3fv(p.u_normal, 1, GL_FALSE, nm.data());
    }

    glBindBuffer(GL_ARRAY_BUFFER, m.vbo);
    glEnableVertexAttribArray(static_cast<GLuint>(p.a_position));
    glVertexAttribPointer(static_cast<GLuint>(p.a_position), 3, GL_FLOAT, GL_FALSE,
                          sizeof(Vertex), reinterpret_cast<void *>(0));
    if (p.a_normal >= 0) {
        glEnableVertexAttribArray(static_cast<GLuint>(p.a_normal));
        glVertexAttribPointer(static_cast<GLuint>(p.a_normal), 3, GL_FLOAT, GL_FALSE,
                              sizeof(Vertex), reinterpret_cast<void *>(sizeof(float) * 3));
    }
    /* Der printbed-Shader liest an derselben Stelle zwei statt drei
     * Werte - dort stehen die Texturkoordinaten. */
    if (p.a_tex_coord >= 0) {
        glEnableVertexAttribArray(static_cast<GLuint>(p.a_tex_coord));
        glVertexAttribPointer(static_cast<GLuint>(p.a_tex_coord), 2, GL_FLOAT, GL_FALSE,
                              sizeof(Vertex), reinterpret_cast<void *>(sizeof(float) * 3));
    }

    glDrawArrays(mode, 0, m.vertex_count);

    glDisableVertexAttribArray(static_cast<GLuint>(p.a_position));
    if (p.a_normal >= 0)
        glDisableVertexAttribArray(static_cast<GLuint>(p.a_normal));
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

} // namespace

/* ------------------------------------------------------------------ */
/* Oeffentliche Schnittstelle                                          */
/* ------------------------------------------------------------------ */

extern "C" {

PSM_API psm_viewport *psm_viewport_create(psm_session *session, const char *shader_dir)
{
    if (session == nullptr || shader_dir == nullptr)
        return nullptr;

    auto *v = new psm_viewport();
    v->session = session;
    v->shader_dir = shader_dir;

    std::string err;
    /* printbed ist nicht lebensnotwendig - ohne ihn bleibt das Bett
     * einfarbig. Deshalb getrennt und ohne Abbruch. */
    if (! link_program(v->prog_bed, v->shader_dir, "printbed", err))
        psm_emit_log(PSM_LOG_WARN, "Bett-Shader: " + err);

    if (! link_program(v->prog_lit, v->shader_dir, "gouraud_light", err) ||
        ! link_program(v->prog_flat, v->shader_dir, "flat", err)) {
        v->last_error = err;
        psm_emit_log(PSM_LOG_ERROR, "Viewport: " + err);
        /* Bewusst nicht abbrechen: die Fehlermeldung ist ueber
         * psm_viewport_last_error abrufbar, und ein leerer Viewport ist
         * besser als ein Absturz beim Start. */
    }

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    return v;
}

PSM_API void psm_viewport_destroy(psm_viewport *v)
{
    if (v == nullptr)
        return;
    for (Mesh &m : v->meshes)
        m.destroy();
    v->bed_fill.destroy();
    v->bed_grid.destroy();
    v->prog_lit.destroy();
    v->prog_flat.destroy();
    v->prog_bed.destroy();
    if (v->bed_tex != 0) { glDeleteTextures(1, &v->bed_tex); v->bed_tex = 0; }
    delete v;
}

PSM_API void psm_viewport_resize(psm_viewport *v, int width, int height)
{
    if (v == nullptr)
        return;
    v->width = std::max(width, 1);
    v->height = std::max(height, 1);
    glViewport(0, 0, v->width, v->height);
}

PSM_API void psm_viewport_invalidate(psm_viewport *v)
{
    if (v != nullptr)
        v->dirty = true;
}

namespace {
/* Weiter unten definiert - die Griffe brauchen Helfer, die erst nach
 * dem Zeichnen stehen. */
void build_gizmo(psm_viewport *v);
}

PSM_API void psm_viewport_render(psm_viewport *v)
{
    if (v == nullptr)
        return;

    if (v->dirty) {
        build_bed(v);
        build_meshes(v);
        v->dirty = false;
    }

    /* Hintergrund wie im Slicer: dunkler Verlauf, hier als Volltonfarbe. */
    glClearColor(0.16f, 0.17f, 0.19f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);

    const Mat4 view = v->view();
    const Mat4 proj = v->projection();

    static const float col_bed[4]   = { 0.27f, 0.29f, 0.31f, 1.f };
    static const float col_grid[4]  = { 0.36f, 0.38f, 0.41f, 1.f };
    static const float col_obj[4]   = { 1.00f, 0.49f, 0.22f, 1.f };  // Prusa-Orange
    static const float col_sel[4]   = { 0.20f, 0.80f, 0.30f, 1.f };

    /* Bett ohne Rueckseitenaussonderung - man schaut auch von unten.
     * Auch in der Vorschau: der Desktop zeigt es dort ebenfalls, und ohne
     * Bezugsflaeche schwebt das Teil im Nichts. */
    glDisable(GL_CULL_FACE);
    if (v->has_bed_model) {
        /* Prusas eigenes Bettmodell. Deutlich heller als der
         * Hintergrund, sonst sieht man nur seinen Umriss - die
         * Beleuchtung des gouraud_light-Shaders zieht die Farbe an den
         * abgewandten Flaechen ohnehin stark herunter. */
        static const float col_model[4] = { 0.55f, 0.56f, 0.58f, 1.f };
        draw(v->prog_lit, v->bed_model, GL_TRIANGLES, view, proj, col_model);
    } else {
        draw(v->prog_flat, v->bed_fill, GL_TRIANGLES, view, proj, col_bed);
    }
    /* Danach die Textur des Druckbereichs darauf. Sie ist teilweise
     * durchsichtig, deshalb Blending an und Tiefenschreiben aus - sonst
     * verdeckt ihr unsichtbarer Rand die Objekte dahinter. */
    if (v->has_bed_texture && v->prog_bed.id != 0) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(GL_FALSE);

        v->prog_bed.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, v->bed_tex);
        if (v->prog_bed.u_texture >= 0)     glUniform1i(v->prog_bed.u_texture, 0);
        if (v->prog_bed.u_transparent >= 0) glUniform1i(v->prog_bed.u_transparent, 0);
        /* svg_source schaltet im Shader den radialen Verlauf hinter der
         * Grafik ein - genau dafuer ist die SVG gemacht. */
        if (v->prog_bed.u_svg_source >= 0)  glUniform1i(v->prog_bed.u_svg_source, 1);

        static const float white[4] = { 1.f, 1.f, 1.f, 1.f };
        draw(v->prog_bed, v->bed_fill, GL_TRIANGLES, view, proj, white);

        glBindTexture(GL_TEXTURE_2D, 0);
        glDepthMask(GL_TRUE);
        glDisable(GL_BLEND);
    } else if (! v->has_bed_model) {
        /* Ohne Modell und ohne Textur bleibt das Raster als Orientierung. */
        draw(v->prog_flat, v->bed_grid, GL_LINES, view, proj, col_grid);
    }
    glEnable(GL_CULL_FACE);

    /* In der Vorschau zeichnet libvgcode die Werkzeugwege - dieselbe
     * Kamera, damit der Wechsel nicht springt. Es setzt seinen eigenen
     * GL-Zustand, deshalb kommt es nach dem Bett. */
    if (v->mode == PSM_VIEW_PREVIEW && v->gcode_loaded) {
        libvgcode::Mat4x4 vm{}, pm{};
        std::memcpy(vm.data(), view.data(), sizeof(float) * 16);
        std::memcpy(pm.data(), proj.data(), sizeof(float) * 16);
        v->gcode_viewer.render(vm, pm);
        return;
    }

    for (const Mesh &m : v->meshes)
        draw(v->prog_lit, m, GL_TRIANGLES, view, proj,
             m.owner == v->selection ? col_sel : col_obj);

    /*
     * Die Griffe zuletzt und ohne Tiefenpruefung: sie sollen immer
     * sichtbar sein, auch wenn sie im Objekt stecken. Genauso macht es
     * der Desktop, sonst verschwindet der Pfeil im Modell und man weiss
     * nicht mehr, wo man anfassen soll.
     */
    if (v->gizmo != PSM_GIZMO_NONE && v->selection != PSM_INVALID_ID) {
        if (v->gizmo_dirty)
            build_gizmo(v);

        glDisable(GL_DEPTH_TEST);
        static const float col_line[4] = { 0.75f, 0.75f, 0.78f, 1.f };
        draw(v->prog_flat, v->gizmo_lines, GL_LINES, view, proj, col_line);

        for (int a = 0; a < v->gizmo_axis_count; ++a) {
            float col[4];
            /* Der vierte Griff ist "gleichmaessig" und bekommt keine
             * Achsenfarbe, sondern das Prusa-Orange. */
            if (a == 3) { col[0] = 0.93f; col[1] = 0.42f; col[2] = 0.13f; col[3] = 1.f; }
            else        psm::axis_color(a, a == v->gizmo_hover, col);

            const GLenum mode = (v->gizmo == PSM_GIZMO_ROTATE) ? GL_LINES : GL_TRIANGLES;
            draw(v->gizmo == PSM_GIZMO_ROTATE ? v->prog_flat : v->prog_lit,
                 v->gizmo_solid[a], mode, view, proj, col);
        }
        glEnable(GL_DEPTH_TEST);
    }
}

/* --- Kamera ------------------------------------------------------- */

PSM_API void psm_viewport_orbit(psm_viewport *v, float dx, float dy)
{
    if (v == nullptr)
        return;
    /* Auf die Bildschirmbreite bezogen statt auf Pixel: ein Wisch ueber
     * die halbe Breite dreht rund 90 Grad, unabhaengig von der Aufloesung.
     * Mit festem Pixelfaktor war es auf einem 2560er Tablet unbrauchbar
     * hektisch. */
    const float per_px = PI_F / static_cast<float>(std::max(v->width, 1));
    v->yaw   += dx * per_px;
    v->pitch += dy * per_px;

    /* Unter das Bett darf man schauen, aber nicht ueber den Pol kippen. */
    const float limit = PI_F * 0.49f;
    v->pitch = std::clamp(v->pitch, -limit, limit);
}

PSM_API void psm_viewport_pan(psm_viewport *v, float dx, float dy)
{
    if (v == nullptr)
        return;
    const Vec3 fwd = (v->target - v->eye()).normalized();
    const Vec3 right = fwd.cross(Vec3(0.f, 0.f, 1.f)).normalized();
    const Vec3 up = right.cross(fwd).normalized();
    const float scale = v->distance * 0.0015f;
    v->target += right * (-dx * scale) + up * (dy * scale);
}

PSM_API void psm_viewport_zoom(psm_viewport *v, float factor)
{
    if (v == nullptr || factor <= 0.f)
        return;
    v->distance = std::clamp(v->distance / factor, 20.f, 5000.f);
}

PSM_API void psm_viewport_reset_view(psm_viewport *v)
{
    if (v == nullptr)
        return;
    v->yaw = -0.6f;
    v->pitch = 0.55f;
    v->dirty = true;   // setzt Ziel und Abstand aus der Bettgroesse neu
}

PSM_API void psm_viewport_view_preset(psm_viewport *v, int which)
{
    if (v == nullptr)
        return;
    /* Feste Blickrichtungen wie in PrusaSlicers Ansichts-Werkzeugleiste. */
    switch (which) {
        case 0: v->yaw = -0.6f;    v->pitch = 0.55f;        break; // iso
        case 1: v->yaw =  0.f;     v->pitch = PI_F * 0.49f; break; // oben
        case 2: v->yaw =  0.f;     v->pitch = 0.f;          break; // vorne
        case 3: v->yaw =  PI_F;    v->pitch = 0.f;          break; // hinten
        case 4: v->yaw = -PI_F/2;  v->pitch = 0.f;          break; // links
        case 5: v->yaw =  PI_F/2;  v->pitch = 0.f;          break; // rechts
        default: break;
    }
}

/* --- Auswahl ------------------------------------------------------ */

PSM_API psm_object_id psm_viewport_pick(psm_viewport *v, float x, float y)
{
    if (v == nullptr || v->meshes.empty())
        return PSM_INVALID_ID;

    /* Strahl aus der Bildschirmposition ruecktransformieren. */
    const Mat4 inv = (v->projection() * v->view()).inverse();
    const float nx = 2.f * x / static_cast<float>(v->width) - 1.f;
    const float ny = 1.f - 2.f * y / static_cast<float>(v->height);

    Eigen::Vector4f p0 = inv * Eigen::Vector4f(nx, ny, -1.f, 1.f);
    Eigen::Vector4f p1 = inv * Eigen::Vector4f(nx, ny,  1.f, 1.f);
    p0 /= p0.w();
    p1 /= p1.w();

    const Slic3r::Vec3d origin(p0.x(), p0.y(), p0.z());
    const Slic3r::Vec3d dir = Slic3r::Vec3d(p1.x() - p0.x(), p1.y() - p0.y(), p1.z() - p0.z()).normalized();

    /* Test gegen die Huellquader. Fuer die Auswahl auf dem Bett genau
     * genug; ein Test gegen jedes Dreieck kommt mit den Gizmos in M5. */
    psm_object_id best = PSM_INVALID_ID;
    double best_t = std::numeric_limits<double>::max();

    for (const Mesh &m : v->meshes) {
        double tmin = 0.0, tmax = std::numeric_limits<double>::max();
        bool hit = true;
        for (int a = 0; a < 3; ++a) {
            if (std::abs(dir[a]) < 1e-9) {
                if (origin[a] < m.bbox.min[a] || origin[a] > m.bbox.max[a]) { hit = false; break; }
            } else {
                double t1 = (m.bbox.min[a] - origin[a]) / dir[a];
                double t2 = (m.bbox.max[a] - origin[a]) / dir[a];
                if (t1 > t2) std::swap(t1, t2);
                tmin = std::max(tmin, t1);
                tmax = std::min(tmax, t2);
                if (tmin > tmax) { hit = false; break; }
            }
        }
        if (hit && tmin < best_t) {
            best_t = tmin;
            best = m.owner;
        }
    }
    return best;
}

PSM_API void psm_viewport_set_selection(psm_viewport *v, psm_object_id id)
{
    if (v != nullptr)
        v->selection = id;
}

namespace {

/** Schnittpunkt des Sehstrahls durch (x,y) mit der Ebene z = plane_z. */
bool ray_to_plane(const psm_viewport *v, float x, float y, double plane_z,
                  Slic3r::Vec3d &out)
{
    const Mat4 inv = (v->projection() * v->view()).inverse();
    const float nx = 2.f * x / static_cast<float>(v->width) - 1.f;
    const float ny = 1.f - 2.f * y / static_cast<float>(v->height);

    Eigen::Vector4f p0 = inv * Eigen::Vector4f(nx, ny, -1.f, 1.f);
    Eigen::Vector4f p1 = inv * Eigen::Vector4f(nx, ny,  1.f, 1.f);
    p0 /= p0.w();
    p1 /= p1.w();

    const Slic3r::Vec3d o(p0.x(), p0.y(), p0.z());
    const Slic3r::Vec3d d = Slic3r::Vec3d(p1.x() - p0.x(), p1.y() - p0.y(),
                                          p1.z() - p0.z()).normalized();
    if (std::abs(d.z()) < 1e-9)
        return false;                      // Blick parallel zum Bett

    const double t = (plane_z - o.z()) / d.z();
    if (t <= 0.0)
        return false;                      // Ebene liegt hinter der Kamera
    out = o + d * t;
    return true;
}

} // namespace

PSM_API int psm_viewport_drag_selected(psm_viewport *v,
                                       float from_x, float from_y,
                                       float to_x, float to_y)
{
    if (v == nullptr || v->selection == PSM_INVALID_ID)
        return 0;

    Slic3r::ModelObject *obj = nullptr;
    for (Slic3r::ModelObject *o : v->session->model.objects)
        if (static_cast<psm_object_id>(o->id().id) == v->selection) {
            obj = o;
            break;
        }
    if (obj == nullptr || obj->instances.empty())
        return 0;

    /* Auf halber Objekthoehe schneiden, nicht auf dem Bett: sonst laeuft
     * das Objekt bei flacher Kamera davon. */
    const Slic3r::BoundingBoxf3 bb = obj->instance_bounding_box(0, false);
    const double plane_z = (bb.min.z() + bb.max.z()) * 0.5;

    Slic3r::Vec3d a, b;
    if (! ray_to_plane(v, from_x, from_y, plane_z, a) ||
        ! ray_to_plane(v, to_x,   to_y,   plane_z, b))
        return 0;

    Slic3r::ModelInstance *inst = obj->instances.front();
    Slic3r::Vec3d off = inst->get_offset();
    off.x() += b.x() - a.x();
    off.y() += b.y() - a.y();
    inst->set_offset(off);
    obj->invalidate_bounding_box();

    v->dirty = true;
    return 1;
}

/* ------------------------------------------------------------------ */

namespace {

/** Das ausgewaehlte Objekt, oder null. */
Slic3r::ModelObject *selected_object(psm_viewport *v)
{
    if (v == nullptr || v->selection == PSM_INVALID_ID)
        return nullptr;
    for (Slic3r::ModelObject *o : v->session->model.objects)
        if (static_cast<psm_object_id>(o->id().id) == v->selection)
            return o;
    return nullptr;
}

/** Mittelpunkt des ausgewaehlten Objekts - dort sitzen die Griffe. */
bool selected_center(psm_viewport *v, Slic3r::Vec3d &out)
{
    Slic3r::ModelObject *o = selected_object(v);
    if (o == nullptr || o->instances.empty())
        return false;
    out = o->instance_bounding_box(0, false).center();
    return true;
}

/*
 * psm::Vertex und der Eckpunkttyp hier sind absichtlich gleich
 * aufgebaut: die Griffe entstehen in einer eigenen Uebersetzungseinheit
 * ohne GL-Kenntnis und werden hier nur hochgeladen.
 */
static_assert(sizeof(psm::Vertex) == sizeof(Vertex),
              "Eckpunkttypen von Viewport und Griffen sind auseinandergelaufen");

void upload_psm(Mesh &m, const std::vector<psm::Vertex> &verts)
{
    upload(m, *reinterpret_cast<const std::vector<Vertex> *>(&verts));
}

/*
 * Baut die Griffe neu.
 *
 * Das passiert bei jeder Kamerabewegung, weil die Griffe feste
 * Bildschirmgroesse haben - ein paar hundert Eckpunkte, das faellt
 * nicht ins Gewicht.
 */
void build_gizmo(psm_viewport *v)
{
    for (Mesh &m : v->gizmo_solid)
        m.destroy();
    v->gizmo_lines.destroy();
    v->gizmo_axis_count = 0;
    v->gizmo_dirty = false;

    if (v->gizmo == PSM_GIZMO_NONE)
        return;

    Slic3r::Vec3d c;
    if (! selected_center(v, c))
        return;

    const psm::Vec3 origin(static_cast<float>(c.x()), static_cast<float>(c.y()),
                           static_cast<float>(c.z()));
    const psm::Mat4 vp = v->projection() * v->view();
    const float len = psm::handle_length_px() *
                      psm::screen_scale(vp, origin, v->width, v->height);

    std::vector<psm::Vertex> lines;

    switch (v->gizmo) {
        case PSM_GIZMO_MOVE:
            for (int a = 0; a < 3; ++a) {
                std::vector<psm::Vertex> arrow;
                psm::build_arrow(arrow, origin, psm::axis_vector(a), len);
                /* Die ersten beiden Eckpunkte sind der Schaft, der Rest
                 * die Spitze - Linien und Dreiecke gehen getrennt. */
                lines.push_back(arrow[0]);
                lines.push_back(arrow[1]);
                upload_psm(v->gizmo_solid[a],
                           std::vector<psm::Vertex>(arrow.begin() + 2, arrow.end()));
            }
            v->gizmo_axis_count = 3;
            break;

        case PSM_GIZMO_ROTATE:
            for (int a = 0; a < 3; ++a) {
                std::vector<psm::Vertex> ring;
                psm::build_circle(ring, origin, a, len, 48);
                upload_psm(v->gizmo_solid[a], ring);
            }
            v->gizmo_axis_count = 3;
            break;

        case PSM_GIZMO_SCALE: {
            const float half = len * 0.075f;
            for (int a = 0; a < 3; ++a) {
                const psm::Vec3 tip = origin + psm::axis_vector(a) * len;
                lines.push_back({ origin.x(), origin.y(), origin.z(), 0.f, 0.f, 1.f });
                lines.push_back({ tip.x(), tip.y(), tip.z(), 0.f, 0.f, 1.f });

                std::vector<psm::Vertex> box;
                psm::build_box(box, tip, half);
                upload_psm(v->gizmo_solid[a], box);
            }
            /* Der Griff fuer gleichmaessiges Skalieren. */
            const psm::Vec3 d = psm::Vec3(1.f, 1.f, 1.f).normalized();
            std::vector<psm::Vertex> box;
            psm::build_box(box, origin + d * (len * 0.75f), half * 1.2f);
            upload_psm(v->gizmo_solid[3], box);
            v->gizmo_axis_count = 4;
            break;
        }

        default:
            break;
    }

    if (! lines.empty())
        upload_psm(v->gizmo_lines, lines);
}

} // namespace

PSM_API int psm_viewport_scale_selected(psm_viewport *v, float factor)
{
    if (v == nullptr || v->selection == PSM_INVALID_ID)
        return 0;
    /* Unsinnige Faktoren abweisen, statt das Objekt zu zerstoeren. */
    if (! (factor > 0.f) || factor > 100.f)
        return 0;

    Slic3r::ModelObject *obj = nullptr;
    for (Slic3r::ModelObject *o : v->session->model.objects)
        if (static_cast<psm_object_id>(o->id().id) == v->selection) {
            obj = o;
            break;
        }
    if (obj == nullptr || obj->instances.empty())
        return 0;

    Slic3r::ModelInstance *inst = obj->instances.front();
    Slic3r::Vec3d sc = inst->get_scaling_factor();

    /* Grenzen wie am Desktop: unter einem Prozent ist nichts mehr zu
     * sehen, ueber dem Hundertfachen passt nichts mehr aufs Bett. */
    const double f = std::clamp(static_cast<double>(factor),
                                0.01 / sc.x(), 100.0 / sc.x());
    sc *= f;
    inst->set_scaling_factor(sc);
    obj->invalidate_bounding_box();

    /* Nach dem Skalieren wieder aufsetzen - sonst schwebt das Objekt
     * beim Verkleinern und steckt beim Vergroessern im Bett. */
    const Slic3r::BoundingBoxf3 bb = obj->instance_bounding_box(0, false);
    Slic3r::Vec3d off = inst->get_offset();
    off.z() -= bb.min.z();
    inst->set_offset(off);
    obj->invalidate_bounding_box();

    v->dirty = true;
    return 1;
}

/* --- Vorschau ------------------------------------------------------ */

PSM_API void psm_viewport_set_mode(psm_viewport *v, psm_view_mode mode)
{
    if (v != nullptr)
        v->mode = mode;
}

PSM_API psm_view_mode psm_viewport_get_mode(psm_viewport *v)
{
    return v == nullptr ? PSM_VIEW_EDITOR : v->mode;
}

PSM_API int psm_viewport_load_preview(psm_viewport *v)
{
    if (v == nullptr || v->session == nullptr || ! v->session->print)
        return 0;

    try {
        if (! v->gcode_viewer_ready) {
            /* libvgcode laedt seine GL-Funktionen selbst. Die Zeichenkette
             * ist die Kontextversion; unter GLES erwartet es "3.0". */
            v->gcode_viewer.init("3.0");
            v->gcode_viewer_ready = true;
        }

        const Slic3r::Print &print = *v->session->print;

        /* Die Zahl der Extruder steht nirgends als eigener Wert - sie ist
         * die Laenge von nozzle_diameter. Genau so leitet PrusaSlicer sie
         * ab. "extruders_count" gibt es nur als Hilfsoption der Tab-GUI
         * und nicht in PrintConfig; opt_int haette dort null geliefert. */
        const auto *nozzles =
            v->session->config.opt<Slic3r::ConfigOptionFloats>("nozzle_diameter");
        const size_t extruders =
            (nozzles != nullptr && ! nozzles->values.empty()) ? nozzles->values.size() : 1;

        /* Umwandlung aus PrusaSlicer selbst - siehe E-12. */
        libvgcode::GCodeInputData data = libvgcode::convert(
            print,
            /* Werkzeugfarben  */ std::vector<std::string>{},
            /* Farbwechsel     */ std::vector<std::string>{},
            /* Custom-G-Code   */ std::vector<Slic3r::CustomGCode::Item>{},
            extruders);

        v->gcode_viewer.load(std::move(data));
        v->gcode_loaded = true;
        return 1;
    } catch (const std::exception &e) {
        v->last_error = std::string("Vorschau: ") + e.what();
        psm_emit_log(PSM_LOG_ERROR, v->last_error);
        return 0;
    }
}

PSM_API int32_t psm_viewport_layer_count(psm_viewport *v)
{
    if (v == nullptr || ! v->gcode_loaded)
        return 0;
    return static_cast<int32_t>(v->gcode_viewer.get_layers_count());
}

PSM_API void psm_viewport_set_layer_range(psm_viewport *v, int32_t first, int32_t last)
{
    if (v == nullptr || ! v->gcode_loaded)
        return;
    v->gcode_viewer.set_layers_view_range(
        static_cast<libvgcode::Interval::value_type>(std::max(first, 0)),
        static_cast<libvgcode::Interval::value_type>(std::max(last, 0)));
}

PSM_API const char *psm_viewport_last_error(psm_viewport *v)
{
    return v == nullptr ? "" : v->last_error.c_str();
}

/* ------------------------------------------------------------------ */
/* Griffe am Objekt                                                    */
/* ------------------------------------------------------------------ */

PSM_API void psm_viewport_set_gizmo(psm_viewport *v, psm_gizmo_mode mode)
{
    if (v == nullptr || v->gizmo == mode)
        return;
    v->gizmo = mode;
    v->gizmo_dirty = true;
}

PSM_API psm_gizmo_mode psm_viewport_get_gizmo(const psm_viewport *v)
{
    return v == nullptr ? PSM_GIZMO_NONE : v->gizmo;
}

PSM_API int psm_viewport_gizmo_pick(psm_viewport *v, float x, float y,
                                    float radius_px)
{
    if (v == nullptr || v->gizmo == PSM_GIZMO_NONE)
        return -1;
    Slic3r::Vec3d c;
    if (! selected_center(v, c))
        return -1;

    const psm::Vec3 origin(static_cast<float>(c.x()), static_cast<float>(c.y()),
                           static_cast<float>(c.z()));
    const psm::Mat4 vp = v->projection() * v->view();
    const float mm = psm::screen_scale(vp, origin, v->width, v->height);

    return psm::pick_anchor(psm::gizmo_anchors(v->gizmo, origin, mm), vp,
                            v->width, v->height, x, y, radius_px);
}

PSM_API int psm_viewport_gizmo_drag(psm_viewport *v, int axis,
                                    float from_x, float from_y,
                                    float to_x, float to_y, int snap)
{
    if (v == nullptr || axis < 0 || v->gizmo == PSM_GIZMO_NONE)
        return 0;
    Slic3r::ModelObject *obj = selected_object(v);
    if (obj == nullptr || obj->instances.empty())
        return 0;
    Slic3r::ModelInstance *inst = obj->instances.front();

    Slic3r::Vec3d c;
    if (! selected_center(v, c))
        return 0;
    const psm::Vec3 origin(static_cast<float>(c.x()), static_cast<float>(c.y()),
                           static_cast<float>(c.z()));
    const psm::Mat4 vp = v->projection() * v->view();
    const float mm = psm::screen_scale(vp, origin, v->width, v->height);

    switch (v->gizmo) {
        case PSM_GIZMO_MOVE: {
            /*
             * Der Bildschirmversatz wird auf die Achse projiziert: nur
             * der Anteil in Achsenrichtung zaehlt, alles quer dazu wird
             * verworfen. Sonst laeuft das Objekt bei schraeger Kamera
             * seitlich weg.
             */
            const psm::Vec3 a = psm::axis_vector(axis);
            psm::Vec2 s0, s1;
            if (! psm::project_point(vp, origin, v->width, v->height, s0) ||
                ! psm::project_point(vp, origin + a * (10.f * mm),
                                     v->width, v->height, s1))
                return 0;
            const psm::Vec2 dir = s1 - s0;
            const float len2 = dir.squaredNorm();
            if (len2 < 0.01f)
                return 0;

            const psm::Vec2 drag(to_x - from_x, to_y - from_y);
            float along = drag.dot(dir) / len2 * 10.f * mm;
            if (snap != 0)
                along = std::round(along);

            Slic3r::Vec3d off = inst->get_offset();
            off(axis) += static_cast<double>(along);
            inst->set_offset(off);
            break;
        }

        case PSM_GIZMO_ROTATE: {
            /*
             * Der Winkel ergibt sich aus der Drehung des Fingers um den
             * Bildschirmmittelpunkt des Objekts. Das ist die Zuordnung,
             * die sich am natuerlichsten anfuehlt - unabhaengig davon,
             * wie flach der Kreis gerade steht.
             */
            psm::Vec2 ctr;
            if (! psm::project_point(vp, origin, v->width, v->height, ctr))
                return 0;
            const float a0 = std::atan2(from_y - ctr.y(), from_x - ctr.x());
            const float a1 = std::atan2(to_y   - ctr.y(), to_x   - ctr.x());
            float deg = (a1 - a0) * 180.f / static_cast<float>(M_PI);

            Slic3r::Vec3d rot = inst->get_rotation();
            double cur = rot(axis) * 180.0 / M_PI + static_cast<double>(deg);
            if (snap != 0)
                cur = std::round(cur / 15.0) * 15.0;   /* wie am Desktop */
            rot(axis) = cur * M_PI / 180.0;
            inst->set_rotation(rot);
            break;
        }

        case PSM_GIZMO_SCALE: {
            /* Abstand vom Mittelpunkt vorher zu nachher - vergroessert
             * sich der Abstand, waechst das Objekt. */
            psm::Vec2 ctr;
            if (! psm::project_point(vp, origin, v->width, v->height, ctr))
                return 0;
            const float d0 = std::hypot(from_x - ctr.x(), from_y - ctr.y());
            const float d1 = std::hypot(to_x - ctr.x(), to_y - ctr.y());
            if (d0 < 1.f)
                return 0;
            const double f = static_cast<double>(d1 / d0);

            Slic3r::Vec3d sc = inst->get_scaling_factor();
            if (axis == 3) {
                sc *= f;                       /* gleichmaessig */
            } else {
                sc(axis) *= f;                 /* nur diese Achse */
            }
            for (int i = 0; i < 3; ++i)
                sc(i) = std::clamp(sc(i), 0.01, 100.0);
            inst->set_scaling_factor(sc);
            break;
        }

        default:
            return 0;
    }

    obj->invalidate_bounding_box();

    /* Nach Drehen und Skalieren wieder aufsetzen - sonst schwebt oder
     * versinkt das Objekt. Beim Verschieben nicht, sonst liesse sich die
     * Hoehe nie aendern. */
    if (v->gizmo != PSM_GIZMO_MOVE) {
        const Slic3r::BoundingBoxf3 bb = obj->instance_bounding_box(0, false);
        Slic3r::Vec3d off = inst->get_offset();
        off.z() -= bb.min.z();
        inst->set_offset(off);
        obj->invalidate_bounding_box();
    }

    v->dirty = true;
    v->gizmo_dirty = true;
    return 1;
}

} /* extern "C" */

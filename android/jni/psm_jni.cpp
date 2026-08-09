/*
 * psm_jni.cpp - JNI-Bruecke zwischen Kotlin und psmobile_core.
 *
 * Wird in libpsmobile_core.so mit einkompiliert, damit die App nur eine
 * einzige .so ausliefern muss und Gradle kein NDK braucht.
 *
 * Wichtig: Der Fortschritts-Callback kommt aus dem Slice-Thread, nicht
 * aus einem Java-Thread. Er muss sich deshalb selbst an die JVM haengen.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <limits>
#include <string>
#include <vector>

#include "psmobile_core.h"
#include "psm_viewport.h"

#define LOG_TAG "psmobile"

namespace {

JavaVM *g_vm = nullptr;

/* Globale Referenz auf das Kotlin-Callback-Objekt des laufenden Jobs. */
struct ProgressBridge {
    jobject    listener = nullptr;   /* global ref */
    jmethodID  on_progress = nullptr;
};

void android_log(psm_log_level lvl, const char *msg, void *)
{
    int prio = ANDROID_LOG_INFO;
    switch (lvl) {
        case PSM_LOG_ERROR: prio = ANDROID_LOG_ERROR; break;
        case PSM_LOG_WARN:  prio = ANDROID_LOG_WARN;  break;
        case PSM_LOG_INFO:  prio = ANDROID_LOG_INFO;  break;
        case PSM_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; break;
    }
    __android_log_write(prio, LOG_TAG, msg);
}

int progress_trampoline(int percent, const char *stage, void *user)
{
    auto *b = static_cast<ProgressBridge *>(user);
    if (b == nullptr || b->listener == nullptr || g_vm == nullptr)
        return 0;

    JNIEnv *env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK)
            return 0;
        attached = true;
    }

    jstring jstage = env->NewStringUTF(stage != nullptr ? stage : "");
    jboolean cancel = env->CallBooleanMethod(b->listener, b->on_progress,
                                            static_cast<jint>(percent), jstage);
    env->DeleteLocalRef(jstage);

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        cancel = JNI_TRUE;
    }

    if (attached)
        g_vm->DetachCurrentThread();

    return cancel == JNI_TRUE ? 1 : 0;
}

std::string jstr(JNIEnv *env, jstring s)
{
    if (s == nullptr)
        return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out = c != nullptr ? c : "";
    if (c != nullptr)
        env->ReleaseStringUTFChars(s, c);
    return out;
}

inline psm_session *sess(jlong handle) { return reinterpret_cast<psm_session *>(handle); }

jobjectArray string_array(JNIEnv *env, const std::vector<std::string> &values)
{
    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr)
        return nullptr;
    jobjectArray out = env->NewObjectArray(
        static_cast<jsize>(values.size()), string_class, nullptr);
    env->DeleteLocalRef(string_class);
    if (out == nullptr)
        return nullptr;
    for (size_t i = 0; i < values.size(); ++i) {
        jstring value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *)
{
    g_vm = vm;
    psm_set_log_callback(android_log, nullptr);
    return JNI_VERSION_1_6;
}

#define JNI_FN(name) Java_de_psmobile_core_PsmCore_##name

JNIEXPORT jint JNICALL JNI_FN(nativeAbiVersion)(JNIEnv *, jclass)
{
    return psm_abi_version();
}

JNIEXPORT jstring JNICALL JNI_FN(nativeCoreVersion)(JNIEnv *env, jclass)
{
    return env->NewStringUTF(psm_core_version());
}

JNIEXPORT jlong JNICALL JNI_FN(nativeCreate)(JNIEnv *env, jclass, jstring datadir, jstring resdir)
{
    const std::string d = jstr(env, datadir);
    const std::string r = jstr(env, resdir);
    return reinterpret_cast<jlong>(psm_session_create(d.c_str(), r.c_str()));
}

JNIEXPORT void JNICALL JNI_FN(nativeDestroy)(JNIEnv *, jclass, jlong h)
{
    psm_session_destroy(sess(h));
}

JNIEXPORT jstring JNICALL JNI_FN(nativeLastError)(JNIEnv *env, jclass, jlong h)
{
    return env->NewStringUTF(psm_last_error(reinterpret_cast<void *>(h)));
}

JNIEXPORT jint JNICALL JNI_FN(nativeClear)(JNIEnv *, jclass, jlong h)
{
    return psm_session_clear(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeLoadPresets)(JNIEnv *, jclass, jlong h)
{
    return psm_presets_load_bundled(sess(h));
}

/* --- Ersteinrichtung -------------------------------------------------- */

JNIEXPORT jint JNICALL JNI_FN(nativeScanPrinterModels)(JNIEnv *, jclass, jlong h)
{
    size_t n = 0;
    if (psm_printer_models_scan(sess(h), &n) != PSM_OK)
        return 0;
    return static_cast<jint>(n);
}

/* "vendor:model\tName\tFamilie\tTechnologie\tVarianten" - ein String statt
 * fuenf JNI-Aufrufen je Modell. */
JNIEXPORT jstring JNICALL JNI_FN(nativePrinterModelAt)(JNIEnv *env, jclass, jlong h, jint index)
{
    psm_printer_model m;
    if (psm_printer_model_at(sess(h), static_cast<size_t>(index), &m) != PSM_OK)
        return nullptr;

    /* Duesengroessen gleich mitliefern - sonst braucht die
     * Ersteinrichtung zehn weitere JNI-Aufrufe je Drucker. */
    std::string variants;
    for (int32_t v = 0; v < m.variant_count; ++v) {
        char buf[64] = { 0 };
        if (psm_printer_variant_at(sess(h), static_cast<size_t>(index),
                                   static_cast<size_t>(v), buf, sizeof(buf)) != PSM_OK)
            continue;
        if (! variants.empty())
            variants += ",";
        variants += buf;
    }

    std::string s = std::string(m.vendor_id) + ":" + m.model_id + "\t" +
                    m.name + "\t" + m.family + "\t" +
                    std::to_string(m.technology) + "\t" +
                    variants;
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeInstallPresets)(JNIEnv *env, jclass, jlong h,
                                                    jobjectArray keys)
{
    const jsize n = keys == nullptr ? 0 : env->GetArrayLength(keys);

    std::vector<std::string> owned;
    std::vector<const char *> ptrs;
    owned.reserve(static_cast<size_t>(n));
    ptrs.reserve(static_cast<size_t>(n));

    for (jsize i = 0; i < n; ++i) {
        auto js = static_cast<jstring>(env->GetObjectArrayElement(keys, i));
        owned.push_back(jstr(env, js));
        env->DeleteLocalRef(js);
    }
    for (const std::string &s : owned)
        ptrs.push_back(s.c_str());

    return psm_presets_install(sess(h), ptrs.empty() ? nullptr : ptrs.data(),
                               ptrs.size());
}

/* --- Konfigurations-Metadaten ----------------------------------------- */

/* "typ\tmodus\thasMin\tmin\thasMax\tmax\tenumCount\tlabel\teinheit\ttooltip" */
JNIEXPORT jstring JNICALL JNI_FN(nativeConfigMeta)(JNIEnv *env, jclass, jlong h, jstring key)
{
    const std::string k = jstr(env, key);
    psm_config_meta m;
    if (psm_config_meta_for(sess(h), k.c_str(), &m) != PSM_OK)
        return nullptr;

    std::string s = std::to_string(static_cast<int>(m.type)) + "\t" +
                    std::to_string(static_cast<int>(m.mode)) + "\t" +
                    std::to_string(m.has_min) + "\t" + std::to_string(m.min) + "\t" +
                    std::to_string(m.has_max) + "\t" + std::to_string(m.max) + "\t" +
                    std::to_string(m.enum_count) + "\t" +
                    m.label + "\t" + m.unit + "\t" + m.tooltip;
    return env->NewStringUTF(s.c_str());
}

/* "wert\tbeschriftung" */
JNIEXPORT jstring JNICALL JNI_FN(nativeConfigEnumAt)(JNIEnv *env, jclass, jlong h,
                                                     jstring key, jint index)
{
    const std::string k = jstr(env, key);
    char value[256] = { 0 };
    char label[256] = { 0 };
    if (psm_config_enum_value_at(sess(h), k.c_str(), static_cast<size_t>(index),
                                 value, sizeof(value), label, sizeof(label)) != PSM_OK)
        return nullptr;
    return env->NewStringUTF((std::string(value) + "\t" + label).c_str());
}

JNIEXPORT jintArray JNICALL JNI_FN(nativeLoadModel)(JNIEnv *env, jclass, jlong h, jstring path)
{
    const std::string p = jstr(env, path);
    psm_object_id ids[256];
    size_t count = 0;
    if (psm_model_load(sess(h), p.c_str(), ids, 256, &count) != PSM_OK)
        return nullptr;

    const jsize n = static_cast<jsize>(count < 256 ? count : 256);
    jintArray arr = env->NewIntArray(n);
    if (arr != nullptr)
        env->SetIntArrayRegion(arr, 0, n, reinterpret_cast<const jint *>(ids));
    return arr;
}

/* configGeladen\tpostProcessEntfernt\tobjekte\tbetten\tangefragterDrucker\t
 * gewaehlterDrucker\tangefragtesDruckprofil\tgewaehltesDruckprofil */
JNIEXPORT jstring JNICALL JNI_FN(nativeLoadProject)(JNIEnv *env, jclass, jlong h,
                                                    jstring path)
{
    const std::string p = jstr(env, path);
    psm_project_import_info info{};
    if (psm_project_load_3mf(sess(h), p.c_str(), &info) != PSM_OK)
        return nullptr;

    const std::string out =
        std::to_string(info.config_loaded) + "\t" +
        std::to_string(info.post_process_removed) + "\t" +
        std::to_string(info.object_count) + "\t" +
        std::to_string(info.bed_count) + "\t" +
        info.requested_printer + "\t" +
        info.selected_printer + "\t" +
        info.requested_print + "\t" +
        info.selected_print;
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeSaveProject)(JNIEnv *env, jclass, jlong h,
                                                  jstring path)
{
    const std::string p = jstr(env, path);
    return psm_project_save_3mf(sess(h), p.c_str());
}

/* --- Undo / Redo ----------------------------------------------------- */

JNIEXPORT jint JNICALL JNI_FN(nativeHistoryBegin)(JNIEnv *env, jclass, jlong h,
                                                   jstring label)
{
    const std::string value = jstr(env, label);
    return psm_history_begin(sess(h), value.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeHistoryEnd)(JNIEnv *, jclass, jlong h)
{
    return psm_history_end(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeUndoCount)(JNIEnv *, jclass, jlong h)
{
    return static_cast<jint>(psm_history_undo_count(sess(h)));
}

JNIEXPORT jint JNICALL JNI_FN(nativeRedoCount)(JNIEnv *, jclass, jlong h)
{
    return static_cast<jint>(psm_history_redo_count(sess(h)));
}

JNIEXPORT jstring JNICALL JNI_FN(nativeUndoLabel)(JNIEnv *env, jclass, jlong h)
{
    char label[256] = { 0 };
    if (psm_history_undo_label(sess(h), label, sizeof(label)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(label);
}

JNIEXPORT jstring JNICALL JNI_FN(nativeRedoLabel)(JNIEnv *env, jclass, jlong h)
{
    char label[256] = { 0 };
    if (psm_history_redo_label(sess(h), label, sizeof(label)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(label);
}

JNIEXPORT jint JNICALL JNI_FN(nativeUndo)(JNIEnv *, jclass, jlong h)
{
    return psm_history_undo(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeRedo)(JNIEnv *, jclass, jlong h)
{
    return psm_history_redo(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeHistoryClear)(JNIEnv *, jclass, jlong h)
{
    return psm_history_clear(sess(h));
}

/* --- Mehrbett -------------------------------------------------------- */

JNIEXPORT jintArray JNICALL JNI_FN(nativeBeds)(JNIEnv *env, jclass, jlong h)
{
    const size_t count = psm_bed_count(sess(h));
    if (count > static_cast<size_t>(std::numeric_limits<jsize>::max()))
        return nullptr;
    std::vector<jint> values(count * 3 + 1);
    values[0] = static_cast<jint>(psm_bed_active(sess(h)));
    for (size_t i = 0; i < count; ++i) {
        psm_bed_metadata metadata{};
        psm_bed_metadata_get(sess(h), i, &metadata);
        values[i * 3 + 1] = static_cast<jint>(psm_bed_object_count(sess(h), i));
        values[i * 3 + 2] = static_cast<jint>(psm_bed_instance_count(sess(h), i));
        values[i * 3 + 3] = metadata.locked;
    }

    jintArray out = env->NewIntArray(static_cast<jsize>(values.size()));
    if (out != nullptr)
        env->SetIntArrayRegion(out, 0, static_cast<jsize>(values.size()),
                               values.data());
    return out;
}

JNIEXPORT jstring JNICALL JNI_FN(nativeBedName)(JNIEnv *env, jclass, jlong h, jint index)
{
    psm_bed_metadata metadata{};
    if (index < 0 || psm_bed_metadata_get(sess(h), static_cast<size_t>(index), &metadata) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(metadata.name);
}

JNIEXPORT jint JNICALL JNI_FN(nativeBedMetadataSet)(JNIEnv *env, jclass, jlong h,
                                                     jint index, jstring name, jboolean locked)
{
    if (index < 0 || name == nullptr)
        return PSM_ERR_INVALID_ARG;
    const char *chars = env->GetStringUTFChars(name, nullptr);
    if (chars == nullptr)
        return PSM_ERR_GENERIC;
    psm_bed_metadata metadata{};
    std::snprintf(metadata.name, sizeof(metadata.name), "%s", chars);
    metadata.locked = locked ? 1 : 0;
    env->ReleaseStringUTFChars(name, chars);
    return psm_bed_metadata_set(sess(h), static_cast<size_t>(index), &metadata);
}

JNIEXPORT jint JNICALL JNI_FN(nativeBedSelect)(JNIEnv *, jclass, jlong h, jint index)
{
    return index < 0 ? PSM_ERR_INVALID_ARG :
        psm_bed_select(sess(h), static_cast<size_t>(index));
}

JNIEXPORT jint JNICALL JNI_FN(nativeBedAdd)(JNIEnv *, jclass, jlong h)
{
    size_t index = 0;
    return psm_bed_add(sess(h), &index) == PSM_OK
        ? static_cast<jint>(index) : -1;
}

JNIEXPORT jint JNICALL JNI_FN(nativeBedRemove)(JNIEnv *, jclass, jlong h, jint index)
{
    return index < 0 ? PSM_ERR_INVALID_ARG :
        psm_bed_remove(sess(h), static_cast<size_t>(index));
}

JNIEXPORT jint JNICALL JNI_FN(nativeBedClear)(JNIEnv *, jclass, jlong h)
{
    return psm_bed_clear(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeBedMoveObject)(JNIEnv *, jclass, jlong h,
                                                   jint id, jint target)
{
    if (target < 0)
        return PSM_INVALID_ID;
    psm_object_id new_id = PSM_INVALID_ID;
    return psm_bed_move_object(sess(h), id, static_cast<size_t>(target),
                               &new_id) == PSM_OK ? new_id : PSM_INVALID_ID;
}

JNIEXPORT jint JNICALL JNI_FN(nativeRemoveModel)(JNIEnv *, jclass, jlong h, jint id)
{
    return psm_model_remove(sess(h), id);
}

JNIEXPORT jintArray JNICALL JNI_FN(nativeListObjects)(JNIEnv *env, jclass, jlong h)
{
    psm_object_id ids[256];
    size_t count = 0;
    if (psm_model_list(sess(h), ids, 256, &count) != PSM_OK)
        return nullptr;
    const jsize n = static_cast<jsize>(count < 256 ? count : 256);
    jintArray arr = env->NewIntArray(n);
    if (arr != nullptr)
        env->SetIntArrayRegion(arr, 0, n, reinterpret_cast<const jint *>(ids));
    return arr;
}

/*
 * Objektinfo wird als flaches float[]/String-Paar zurueckgegeben statt als
 * Java-Objekt: das spart pro Aufruf ein Dutzend JNI-Reflection-Schritte.
 * Layout: [posX,posY,posZ, rotX,rotY,rotZ, sclX,sclY,sclZ,
 *          bbMinX,bbMinY,bbMinZ, bbMaxX,bbMaxY,bbMaxZ,
 *          triangles, instances, outsideBed]
 */
JNIEXPORT jfloatArray JNICALL JNI_FN(nativeObjectInfo)(JNIEnv *env, jclass, jlong h, jint id)
{
    psm_object_info info;
    if (psm_model_info(sess(h), id, &info) != PSM_OK)
        return nullptr;

    float v[18];
    std::memcpy(v + 0,  info.position, 3 * sizeof(float));
    std::memcpy(v + 3,  info.rotation, 3 * sizeof(float));
    std::memcpy(v + 6,  info.scale,    3 * sizeof(float));
    std::memcpy(v + 9,  info.bbox_min, 3 * sizeof(float));
    std::memcpy(v + 12, info.bbox_max, 3 * sizeof(float));
    v[15] = static_cast<float>(info.triangle_count);
    v[16] = static_cast<float>(info.instance_count);
    v[17] = static_cast<float>(info.outside_bed);

    jfloatArray arr = env->NewFloatArray(18);
    if (arr != nullptr)
        env->SetFloatArrayRegion(arr, 0, 18, v);
    return arr;
}

JNIEXPORT jstring JNICALL JNI_FN(nativeObjectName)(JNIEnv *env, jclass, jlong h, jint id)
{
    psm_object_info info;
    if (psm_model_info(sess(h), id, &info) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(info.name);
}

JNIEXPORT jint JNICALL JNI_FN(nativeObjectExtruder)(JNIEnv *, jclass,
                                                     jlong h, jint id)
{
    return psm_model_extruder_get(sess(h), id);
}

JNIEXPORT jint JNICALL JNI_FN(nativeObjectExtruderSet)(JNIEnv *, jclass,
                                                        jlong h, jint id,
                                                        jint extruder)
{
    return psm_model_extruder_set(sess(h), id, extruder);
}

JNIEXPORT jint JNICALL JNI_FN(nativeVolumeCount)(JNIEnv *, jclass,
                                                  jlong h, jint id)
{
    return static_cast<jint>(psm_model_volume_count(sess(h), id));
}

/* type\ttriangleCount\teffectiveExtruder\texplicitExtruder\tname */
JNIEXPORT jstring JNICALL JNI_FN(nativeVolumeInfo)(JNIEnv *env, jclass,
                                                   jlong h, jint id,
                                                   jint index)
{
    if (index < 0)
        return nullptr;
    psm_volume_info info{};
    if (psm_model_volume_info(sess(h), id, static_cast<size_t>(index),
                              &info) != PSM_OK)
        return nullptr;
    std::string name = info.name;
    std::replace(name.begin(), name.end(), '\t', ' ');
    std::replace(name.begin(), name.end(), '\n', ' ');
    const std::string value =
        std::to_string(static_cast<int>(info.type)) + "\t" +
        std::to_string(info.triangle_count) + "\t" +
        std::to_string(info.extruder) + "\t" +
        std::to_string(info.explicit_extruder) + "\t" + name;
    return env->NewStringUTF(value.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeVolumeExtruderSet)(JNIEnv *, jclass,
                                                        jlong h, jint id,
                                                        jint index,
                                                        jint extruder)
{
    return index < 0 ? PSM_ERR_INVALID_ARG :
        psm_model_volume_extruder_set(
            sess(h), id, static_cast<size_t>(index), extruder);
}

JNIEXPORT jint JNICALL JNI_FN(nativeSetPosition)(JNIEnv *, jclass, jlong h, jint id,
                                                 jfloat x, jfloat y, jfloat z)
{
    return psm_model_set_position(sess(h), id, x, y, z);
}

JNIEXPORT jint JNICALL JNI_FN(nativeSetRotation)(JNIEnv *, jclass, jlong h, jint id,
                                                 jfloat x, jfloat y, jfloat z)
{
    return psm_model_set_rotation(sess(h), id, x, y, z);
}

JNIEXPORT jint JNICALL JNI_FN(nativeSetScale)(JNIEnv *, jclass, jlong h, jint id,
                                              jfloat x, jfloat y, jfloat z)
{
    return psm_model_set_scale(sess(h), id, x, y, z);
}

JNIEXPORT jint JNICALL JNI_FN(nativeDropToBed)(JNIEnv *, jclass, jlong h, jint id)
{
    return psm_model_drop_to_bed(sess(h), id);
}

JNIEXPORT jint JNICALL JNI_FN(nativeArrange)(JNIEnv *, jclass, jlong h, jfloat gapMm)
{
    return psm_arrange(sess(h), gapMm);
}

JNIEXPORT jint JNICALL JNI_FN(nativeScaleToFit)(JNIEnv *, jclass, jlong h, jint id, jfloat sizeMm)
{
    return psm_model_scale_to_fit(sess(h), id, sizeMm);
}

JNIEXPORT jint JNICALL JNI_FN(nativeFitToBed)(JNIEnv *, jclass, jlong h, jint id,
                                              jfloat fillRatio)
{
    return psm_model_fit_to_bed(sess(h), id, fillRatio);
}

JNIEXPORT jint JNICALL JNI_FN(nativeDuplicate)(JNIEnv *, jclass, jlong h, jint id)
{
    psm_object_id nid = PSM_INVALID_ID;
    if (psm_model_duplicate(sess(h), id, &nid) != PSM_OK)
        return PSM_INVALID_ID;
    return nid;
}

/* --- Erweiterte Modellwerkzeuge -------------------------------------- */

JNIEXPORT jintArray JNICALL JNI_FN(nativeSplitObjects)(JNIEnv *env, jclass,
                                                       jlong h, jint id)
{
    psm_object_id ids[256];
    size_t count = 0;
    if (psm_model_split_objects(sess(h), id, ids, 256, &count) != PSM_OK)
        return nullptr;
    const jsize n = static_cast<jsize>(std::min<size_t>(count, 256));
    jintArray out = env->NewIntArray(n);
    if (out != nullptr)
        env->SetIntArrayRegion(
            out, 0, n, reinterpret_cast<const jint *>(ids));
    return out;
}

JNIEXPORT jint JNICALL JNI_FN(nativeSplitVolumes)(JNIEnv *, jclass,
                                                   jlong h, jint id)
{
    size_t count = 0;
    return psm_model_split_volumes(sess(h), id, &count) == PSM_OK
        ? static_cast<jint>(count) : -1;
}

JNIEXPORT jintArray JNICALL JNI_FN(nativeCutZ)(JNIEnv *env, jclass,
                                                jlong h, jint id,
                                                jfloat z, jboolean upper,
                                                jboolean lower,
                                                jboolean parts)
{
    psm_object_id ids[16];
    size_t count = 0;
    if (psm_model_cut_z(sess(h), id, z,
                        upper == JNI_TRUE, lower == JNI_TRUE,
                        parts == JNI_TRUE, ids, 16, &count) != PSM_OK)
        return nullptr;
    const jsize n = static_cast<jsize>(std::min<size_t>(count, 16));
    jintArray out = env->NewIntArray(n);
    if (out != nullptr)
        env->SetIntArrayRegion(
            out, 0, n, reinterpret_cast<const jint *>(ids));
    return out;
}

JNIEXPORT jintArray JNICALL JNI_FN(nativeSimplify)(JNIEnv *env, jclass,
                                                    jlong h, jint id,
                                                    jfloat ratio)
{
    uint32_t before = 0, after = 0;
    if (psm_model_simplify(sess(h), id, ratio,
                           &before, &after) != PSM_OK)
        return nullptr;
    const jint values[2] = {
        static_cast<jint>(std::min<uint32_t>(
            before, static_cast<uint32_t>(INT32_MAX))),
        static_cast<jint>(std::min<uint32_t>(
            after, static_cast<uint32_t>(INT32_MAX)))
    };
    jintArray out = env->NewIntArray(2);
    if (out != nullptr)
        env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}

JNIEXPORT jint JNICALL JNI_FN(nativeAddPrimitiveVolume)(
    JNIEnv *, jclass, jlong h, jint id, jint type, jint shape,
    jfloat sx, jfloat sy, jfloat sz)
{
    size_t index = 0;
    return psm_model_add_primitive_volume(
               sess(h), id, static_cast<psm_volume_type>(type),
               static_cast<psm_primitive_shape>(shape),
               sx, sy, sz, &index) == PSM_OK
        ? static_cast<jint>(index) : -1;
}

JNIEXPORT jint JNICALL JNI_FN(nativeRemoveVolume)(
    JNIEnv *, jclass, jlong h, jint id, jint index)
{
    return index < 0 ? PSM_ERR_INVALID_ARG :
        psm_model_remove_volume(
            sess(h), id, static_cast<size_t>(index));
}

JNIEXPORT jint JNICALL JNI_FN(nativeAddTextVolume)(
    JNIEnv *env, jclass, jlong h, jint id, jstring text,
    jstring fontPath, jfloat size, jfloat depth, jint type)
{
    const std::string text_value = jstr(env, text);
    const std::string font_value = jstr(env, fontPath);
    size_t index = 0;
    return psm_model_add_text_volume(
               sess(h), id, text_value.c_str(), font_value.c_str(),
               size, depth, static_cast<psm_volume_type>(type),
               &index) == PSM_OK
        ? static_cast<jint>(index) : -1;
}

JNIEXPORT jint JNICALL JNI_FN(nativeAddSvgVolume)(
    JNIEnv *env, jclass, jlong h, jint id, jstring path,
    jfloat depth, jint type)
{
    const std::string value = jstr(env, path);
    size_t index = 0;
    return psm_model_add_svg_volume(
               sess(h), id, value.c_str(), depth,
               static_cast<psm_volume_type>(type),
               &index) == PSM_OK
        ? static_cast<jint>(index) : -1;
}

JNIEXPORT jint JNICALL JNI_FN(nativeLayOnFacet)(
    JNIEnv *, jclass, jlong h, jint id, jint volume, jint facet)
{
    return volume < 0 || facet < 0 ? PSM_ERR_INVALID_ARG :
        psm_model_lay_on_facet(
            sess(h), id, static_cast<size_t>(volume),
            static_cast<size_t>(facet));
}

JNIEXPORT jint JNICALL JNI_FN(nativePaintFacet)(
    JNIEnv *, jclass, jlong h, jint id, jint volume, jint facet,
    jint tool, jint state, jfloat radius)
{
    return volume < 0 || facet < 0 ? PSM_ERR_INVALID_ARG :
        psm_model_paint_brush(
            sess(h), id, static_cast<size_t>(volume),
            static_cast<size_t>(facet),
            static_cast<psm_paint_tool>(tool), state, radius);
}

JNIEXPORT jint JNICALL JNI_FN(nativeClearPaint)(
    JNIEnv *, jclass, jlong h, jint id, jint tool)
{
    return psm_model_clear_paint(
        sess(h), id, static_cast<psm_paint_tool>(tool));
}

JNIEXPORT jint JNICALL JNI_FN(nativeLayerProfileSet)(
    JNIEnv *env, jclass, jlong h, jint id, jdoubleArray values)
{
    if (values == nullptr)
        return psm_model_layer_profile_set(sess(h), id, nullptr, 0);
    const jsize count = env->GetArrayLength(values);
    if ((count & 1) != 0)
        return PSM_ERR_INVALID_ARG;
    std::vector<double> data(static_cast<size_t>(count));
    env->GetDoubleArrayRegion(values, 0, count, data.data());
    return psm_model_layer_profile_set(
        sess(h), id, data.data(), data.size() / 2);
}

JNIEXPORT jdoubleArray JNICALL JNI_FN(nativeLayerProfile)(
    JNIEnv *env, jclass, jlong h, jint id)
{
    const size_t count = psm_model_layer_profile_count(sess(h), id);
    std::vector<double> data(count * 2);
    for (size_t i = 0; i < count; ++i)
        if (psm_model_layer_profile_at(
                sess(h), id, i, &data[i * 2],
                &data[i * 2 + 1]) != PSM_OK)
            return nullptr;
    jdoubleArray out =
        env->NewDoubleArray(static_cast<jsize>(data.size()));
    if (out != nullptr && ! data.empty())
        env->SetDoubleArrayRegion(
            out, 0, static_cast<jsize>(data.size()), data.data());
    return out;
}

JNIEXPORT jstring JNICALL JNI_FN(nativeObjectColour)(
    JNIEnv *env, jclass, jlong h, jint id)
{
    char value[256] = { 0 };
    if (psm_model_colour_get(
            sess(h), id, value, sizeof(value)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(value);
}

JNIEXPORT jint JNICALL JNI_FN(nativeObjectColourSet)(
    JNIEnv *env, jclass, jlong h, jint id, jstring value)
{
    const std::string color = jstr(env, value);
    return psm_model_colour_set(sess(h), id, color.c_str());
}

JNIEXPORT jintArray JNICALL JNI_FN(nativeObjectWipe)(
    JNIEnv *env, jclass, jlong h, jint id)
{
    int32_t infill = 0, objects = 0;
    if (psm_model_wipe_get(
            sess(h), id, &infill, &objects) != PSM_OK)
        return nullptr;
    const jint values[2] = { infill, objects };
    jintArray out = env->NewIntArray(2);
    if (out != nullptr)
        env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}

JNIEXPORT jint JNICALL JNI_FN(nativeObjectWipeSet)(
    JNIEnv *, jclass, jlong h, jint id,
    jboolean infill, jboolean objects)
{
    return psm_model_wipe_set(
        sess(h), id, infill == JNI_TRUE, objects == JNI_TRUE);
}

/* --- Custom-G-Code und Wipe-Tower ------------------------------------ */

JNIEXPORT jint JNICALL JNI_FN(nativeCustomGcodeCount)(
    JNIEnv *, jclass, jlong h)
{
    return static_cast<jint>(psm_custom_gcode_count(sess(h)));
}

JNIEXPORT jobjectArray JNICALL JNI_FN(nativeCustomGcodeAt)(
    JNIEnv *env, jclass, jlong h, jint index)
{
    if (index < 0)
        return nullptr;
    psm_custom_gcode item{};
    if (psm_custom_gcode_at(
            sess(h), static_cast<size_t>(index), &item) != PSM_OK)
        return nullptr;
    return string_array(env, {
        std::to_string(item.print_z),
        std::to_string(static_cast<int>(item.type)),
        std::to_string(item.extruder),
        item.color,
        item.extra
    });
}

psm_custom_gcode jni_custom_gcode(
    JNIEnv *env, jdouble z, jint type, jint extruder,
    jstring color, jstring extra)
{
    psm_custom_gcode item{};
    item.print_z = z;
    item.type = static_cast<psm_custom_gcode_type>(type);
    item.extruder = extruder;
    const std::string c = jstr(env, color);
    const std::string e = jstr(env, extra);
    std::strncpy(item.color, c.c_str(), sizeof(item.color) - 1);
    std::strncpy(item.extra, e.c_str(), sizeof(item.extra) - 1);
    return item;
}

JNIEXPORT jint JNICALL JNI_FN(nativeCustomGcodeAdd)(
    JNIEnv *env, jclass, jlong h, jdouble z, jint type,
    jint extruder, jstring color, jstring extra)
{
    const psm_custom_gcode item =
        jni_custom_gcode(env, z, type, extruder, color, extra);
    return psm_custom_gcode_add(sess(h), &item);
}

JNIEXPORT jint JNICALL JNI_FN(nativeCustomGcodeUpdate)(
    JNIEnv *env, jclass, jlong h, jint index, jdouble z, jint type,
    jint extruder, jstring color, jstring extra)
{
    if (index < 0)
        return PSM_ERR_INVALID_ARG;
    const psm_custom_gcode item =
        jni_custom_gcode(env, z, type, extruder, color, extra);
    return psm_custom_gcode_update(
        sess(h), static_cast<size_t>(index), &item);
}

JNIEXPORT jint JNICALL JNI_FN(nativeCustomGcodeRemove)(
    JNIEnv *, jclass, jlong h, jint index)
{
    return index < 0 ? PSM_ERR_INVALID_ARG :
        psm_custom_gcode_remove(sess(h), static_cast<size_t>(index));
}

JNIEXPORT jint JNICALL JNI_FN(nativeCustomGcodeClear)(
    JNIEnv *, jclass, jlong h)
{
    return psm_custom_gcode_clear(sess(h));
}

JNIEXPORT jfloatArray JNICALL JNI_FN(nativeWipeTower)(
    JNIEnv *env, jclass, jlong h)
{
    float values[3] = {};
    if (psm_wipe_tower_get(
            sess(h), &values[0], &values[1], &values[2]) != PSM_OK)
        return nullptr;
    jfloatArray out = env->NewFloatArray(3);
    if (out != nullptr)
        env->SetFloatArrayRegion(out, 0, 3, values);
    return out;
}

JNIEXPORT jint JNICALL JNI_FN(nativeWipeTowerSet)(
    JNIEnv *, jclass, jlong h, jfloat x, jfloat y, jfloat rotation)
{
    return psm_wipe_tower_set(sess(h), x, y, rotation);
}

/* --- Presets ---------------------------------------------------------- */

JNIEXPORT jint JNICALL JNI_FN(nativePresetCount)(JNIEnv *, jclass, jlong h, jint type)
{
    return static_cast<jint>(psm_preset_count(sess(h), static_cast<psm_preset_type>(type)));
}

JNIEXPORT jstring JNICALL JNI_FN(nativePresetNameAt)(JNIEnv *env, jclass, jlong h,
                                                     jint type, jint index)
{
    char buf[256] = { 0 };
    if (psm_preset_name_at(sess(h), static_cast<psm_preset_type>(type),
                           static_cast<size_t>(index), buf, sizeof(buf)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetSelect)(JNIEnv *env, jclass, jlong h,
                                                  jint type, jstring name)
{
    const std::string n = jstr(env, name);
    return psm_preset_select(sess(h), static_cast<psm_preset_type>(type), n.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetShowIncompatible)(JNIEnv *, jclass,
                                                            jlong h, jboolean on)
{
    return psm_preset_show_incompatible(sess(h), on ? 1 : 0);
}

JNIEXPORT jboolean JNICALL JNI_FN(nativePresetShowsIncompatible)(JNIEnv *, jclass, jlong h)
{
    return psm_preset_shows_incompatible(sess(h)) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL JNI_FN(nativePresetCompatibleAt)(JNIEnv *, jclass, jlong h,
                                                            jint type, jint index)
{
    int32_t ok = 0;
    if (psm_preset_compatible_at(sess(h), static_cast<psm_preset_type>(type),
                                 static_cast<size_t>(index), &ok) != PSM_OK)
        return JNI_TRUE;   /* im Zweifel nicht als unpassend markieren */
    return ok != 0 ? JNI_TRUE : JNI_FALSE;
}

/* --- Extruder, geaenderte Werte, Filamenthersteller ------------------- */
/*
 * Alle Listen kommen als String-Array oder als "\n"-getrennter String
 * hoch, statt Feld fuer Feld ueber JNI. Ein MMU hat fuenf Extruder und
 * ein Drucker gut zwei Dutzend Hersteller - das lohnt keinen Aufruf je
 * Eintrag.
 */

JNIEXPORT jstring JNICALL JNI_FN(nativeConfigGetAt)(JNIEnv *env, jclass, jlong h,
                                                    jstring key, jint index)
{
    const std::string k = jstr(env, key);
    char buf[1024] = { 0 };
    if (psm_config_get_at(sess(h), k.c_str(), index, buf, sizeof(buf)) != PSM_OK)
        return nullptr;
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativeConfigSetAt)(JNIEnv *env, jclass, jlong h,
                                                 jstring key, jint index, jstring value)
{
    const std::string k = jstr(env, key);
    const std::string v = jstr(env, value);
    return psm_config_set_at(sess(h), k.c_str(), index, v.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeExtruderCount)(JNIEnv *, jclass, jlong h)
{
    return psm_extruder_count(sess(h));
}

JNIEXPORT jstring JNICALL JNI_FN(nativeExtruderFilament)(JNIEnv *env, jclass, jlong h, jint idx)
{
    char buf[256] = { 0 };
    if (psm_extruder_filament_get(sess(h), idx, buf, sizeof(buf)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativeExtruderFilamentSet)(JNIEnv *env, jclass, jlong h,
                                                         jint idx, jstring name)
{
    const std::string n = jstr(env, name);
    return psm_extruder_filament_set(sess(h), idx, n.c_str());
}

JNIEXPORT jstring JNICALL JNI_FN(nativeExtruderColor)(JNIEnv *env, jclass, jlong h, jint idx)
{
    char buf[32] = { 0 };
    if (psm_extruder_color_get(sess(h), idx, buf, sizeof(buf)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativeExtruderColorSet)(JNIEnv *env, jclass, jlong h,
                                                      jint idx, jstring rgb)
{
    const std::string c = jstr(env, rgb);
    return psm_extruder_color_set(sess(h), idx, c.c_str());
}

JNIEXPORT jstring JNICALL JNI_FN(nativeColorMixJson)(JNIEnv *env, jclass, jlong h)
{
    char buf[65536] = { 0 };
    if (psm_colormix_get_json(sess(h), buf, sizeof(buf)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativeColorMixSetJson)(JNIEnv *env, jclass, jlong h,
                                                      jstring json)
{
    const std::string source = jstr(env, json);
    return psm_colormix_set_json(sess(h), source.c_str());
}

/** Je Zeile: Name \t Zahl der Filamente \t 1 oder 0 fuer eingeblendet. */
JNIEXPORT jstring JNICALL JNI_FN(nativeFilamentVendors)(JNIEnv *env, jclass, jlong h)
{
    std::string out;
    const size_t n = psm_filament_vendor_count(sess(h));
    for (size_t i = 0; i < n; ++i) {
        char    name[128] = { 0 };
        int32_t count = 0, enabled = 0;
        if (psm_filament_vendor_at(sess(h), i, name, sizeof(name), &count, &enabled) != PSM_OK)
            continue;
        if (! out.empty()) out += '\n';
        out += name;
        out += '\t';
        out += std::to_string(count);
        out += '\t';
        out += std::to_string(enabled);
    }
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeFilamentVendorsSet)(JNIEnv *env, jclass, jlong h,
                                                        jobjectArray names)
{
    std::vector<std::string> owned;
    std::vector<const char *> ptrs;
    const jsize n = names == nullptr ? 0 : env->GetArrayLength(names);
    owned.reserve(static_cast<size_t>(n));
    ptrs.reserve(static_cast<size_t>(n));
    for (jsize i = 0; i < n; ++i) {
        auto js = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        owned.push_back(jstr(env, js));
        env->DeleteLocalRef(js);
    }
    for (const std::string &s : owned)
        ptrs.push_back(s.c_str());
    return psm_filament_vendors_set(sess(h), ptrs.empty() ? nullptr : ptrs.data(),
                                    ptrs.size());
}

/** Je Zeile: Parametername \t Wert im Preset \t eingestellter Wert. */
JNIEXPORT jstring JNICALL JNI_FN(nativePresetDirty)(JNIEnv *env, jclass, jlong h, jint type)
{
    const auto t = static_cast<psm_preset_type>(type);
    std::string out;
    const size_t n = psm_preset_dirty_count(sess(h), t);
    for (size_t i = 0; i < n; ++i) {
        char key[128] = { 0 }, was[512] = { 0 }, now[512] = { 0 };
        if (psm_preset_dirty_at(sess(h), t, i, key, sizeof(key),
                                was, sizeof(was), now, sizeof(now)) != PSM_OK)
            continue;
        if (! out.empty()) out += '\n';
        out += key; out += '\t'; out += was; out += '\t'; out += now;
    }
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetDiscard)(JNIEnv *, jclass, jlong h, jint type)
{
    return psm_preset_discard(sess(h), static_cast<psm_preset_type>(type));
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetSaveAs)(JNIEnv *env, jclass, jlong h,
                                                  jint type, jstring name)
{
    const std::string n = jstr(env, name);
    return psm_preset_save_as(sess(h), static_cast<psm_preset_type>(type), n.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetSelectKeeping)(JNIEnv *env, jclass, jlong h,
                                                         jint type, jstring name,
                                                         jobjectArray keys,
                                                         jobjectArray values)
{
    const std::string n = jstr(env, name);

    std::vector<std::string> ks, vs;
    std::vector<const char *> kp, vp;
    const jsize cnt = keys == nullptr ? 0 : env->GetArrayLength(keys);
    for (jsize i = 0; i < cnt; ++i) {
        auto jk = static_cast<jstring>(env->GetObjectArrayElement(keys, i));
        auto jv = static_cast<jstring>(env->GetObjectArrayElement(values, i));
        ks.push_back(jstr(env, jk));
        vs.push_back(jstr(env, jv));
        env->DeleteLocalRef(jk);
        env->DeleteLocalRef(jv);
    }
    for (size_t i = 0; i < ks.size(); ++i) {
        kp.push_back(ks[i].c_str());
        vp.push_back(vs[i].c_str());
    }
    return psm_preset_select_keeping(sess(h), static_cast<psm_preset_type>(type), n.c_str(),
                                     kp.empty() ? nullptr : kp.data(),
                                     vp.empty() ? nullptr : vp.data(),
                                     kp.size());
}

JNIEXPORT jstring JNICALL JNI_FN(nativePresetSelected)(JNIEnv *env, jclass, jlong h, jint type)
{
    char buf[256] = { 0 };
    if (psm_preset_selected(sess(h), static_cast<psm_preset_type>(type), buf, sizeof(buf)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(buf);
}

/* --- Konfiguration ---------------------------------------------------- */

JNIEXPORT jstring JNICALL JNI_FN(nativeConfigGet)(JNIEnv *env, jclass, jlong h, jstring key)
{
    const std::string k = jstr(env, key);
    char buf[65536] = { 0 };
    if (psm_config_get(sess(h), k.c_str(), buf, sizeof(buf)) != PSM_OK)
        return nullptr;
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativeConfigSet)(JNIEnv *env, jclass, jlong h,
                                               jstring key, jstring value)
{
    const std::string k = jstr(env, key);
    const std::string v = jstr(env, value);
    return psm_config_set(sess(h), k.c_str(), v.c_str());
}

JNIEXPORT jstring JNICALL JNI_FN(nativePresetConfigGet)(JNIEnv *env, jclass, jlong h,
                                                        jint type, jstring key)
{
    const std::string k = jstr(env, key);
    char buf[65536] = { 0 };
    if (psm_preset_config_get(sess(h), static_cast<psm_preset_type>(type),
                              k.c_str(), buf, sizeof(buf)) != PSM_OK)
        return nullptr;
    return env->NewStringUTF(buf);
}

JNIEXPORT jint JNICALL JNI_FN(nativePresetConfigSet)(JNIEnv *env, jclass, jlong h,
                                                     jint type, jstring key,
                                                     jstring value)
{
    const std::string k = jstr(env, key);
    const std::string v = jstr(env, value);
    return psm_preset_config_set(sess(h), static_cast<psm_preset_type>(type),
                                 k.c_str(), v.c_str());
}

/* --- Slicing ---------------------------------------------------------- */

JNIEXPORT jlong JNICALL JNI_FN(nativeSliceStart)(JNIEnv *env, jclass, jlong h, jobject listener)
{
    auto *b = new ProgressBridge();
    if (listener != nullptr) {
        b->listener = env->NewGlobalRef(listener);
        jclass cls  = env->GetObjectClass(listener);
        b->on_progress = env->GetMethodID(cls, "onProgress", "(ILjava/lang/String;)Z");
        env->DeleteLocalRef(cls);
    }

    if (psm_slice_start(sess(h), progress_trampoline, b) != PSM_OK) {
        if (b->listener != nullptr)
            env->DeleteGlobalRef(b->listener);
        delete b;
        return 0;
    }
    /* Handle zurueckgeben, damit Kotlin die Bruecke spaeter freigeben kann. */
    return reinterpret_cast<jlong>(b);
}

JNIEXPORT void JNICALL JNI_FN(nativeSliceReleaseBridge)(JNIEnv *env, jclass, jlong bridge)
{
    auto *b = reinterpret_cast<ProgressBridge *>(bridge);
    if (b == nullptr)
        return;
    if (b->listener != nullptr)
        env->DeleteGlobalRef(b->listener);
    delete b;
}

JNIEXPORT void JNICALL JNI_FN(nativeSliceCancel)(JNIEnv *, jclass, jlong h)
{
    psm_slice_cancel(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeSliceState)(JNIEnv *, jclass, jlong h)
{
    return psm_slice_state_get(sess(h));
}

JNIEXPORT jint JNICALL JNI_FN(nativeSliceWait)(JNIEnv *, jclass, jlong h, jint timeoutMs)
{
    return psm_slice_wait(sess(h), timeoutMs);
}

/* [printTimeSec, filamentMm, filamentG, cost, layers, maxZ, objects] */
JNIEXPORT jdoubleArray JNICALL JNI_FN(nativeSliceStats)(JNIEnv *env, jclass, jlong h)
{
    psm_slice_stats st;
    if (psm_slice_stats_get(sess(h), &st) != PSM_OK)
        return nullptr;
    double v[7] = {
        st.print_time_seconds, st.filament_used_mm, st.filament_used_g,
        st.filament_cost, static_cast<double>(st.layer_count),
        static_cast<double>(st.max_z), static_cast<double>(st.object_count)
    };
    jdoubleArray arr = env->NewDoubleArray(7);
    if (arr != nullptr)
        env->SetDoubleArrayRegion(arr, 0, 7, v);
    return arr;
}

JNIEXPORT jint JNICALL JNI_FN(nativeGcodeExport)(JNIEnv *env, jclass, jlong h, jstring path)
{
    const std::string p = jstr(env, path);
    return psm_gcode_export(sess(h), p.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativePlateExport)(
    JNIEnv *env, jclass, jlong h, jstring path, jint format)
{
    const std::string p = jstr(env, path);
    return format == 0
        ? psm_plate_export_stl(sess(h), p.c_str())
        : psm_plate_export_obj(sess(h), p.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeRepairStl)(
    JNIEnv *env, jclass, jlong h, jstring input, jstring output)
{
    const std::string in = jstr(env, input);
    const std::string out = jstr(env, output);
    return psm_stl_repair(sess(h), in.c_str(), out.c_str());
}

JNIEXPORT jint JNICALL JNI_FN(nativeConvertGcode)(
    JNIEnv *env, jclass, jlong h, jstring input, jstring output,
    jboolean binary)
{
    const std::string in = jstr(env, input);
    const std::string out = jstr(env, output);
    return psm_gcode_convert(
        sess(h), in.c_str(), out.c_str(), binary == JNI_TRUE);
}

JNIEXPORT jint JNICALL JNI_FN(nativeMirror)(JNIEnv *, jclass, jlong h, jint id, jint axis)
{
    return psm_model_mirror(sess(h), static_cast<psm_object_id>(id), axis);
}

JNIEXPORT jint JNICALL JNI_FN(nativeSetInstances)(JNIEnv *, jclass, jlong h, jint id, jint n)
{
    return psm_model_set_instances(sess(h), static_cast<psm_object_id>(id), n);
}

/** "1" oder "0", gefolgt von Tab und dem Grund der Sperre. */
JNIEXPORT jstring JNICALL JNI_FN(nativeConfigEnabled)(JNIEnv *env, jclass, jlong h,
                                                      jstring key)
{
    const std::string k = jstr(env, key);
    char reason[128] = { 0 };
    const int32_t on = psm_config_enabled(sess(h), k.c_str(), reason, sizeof(reason));
    const std::string out = std::string(on ? "1" : "0") + "	" + reason;
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jstring JNICALL JNI_FN(nativeGcodeSuggestedName)(JNIEnv *env, jclass, jlong h)
{
    char buf[512] = { 0 };
    if (psm_gcode_suggested_name(sess(h), buf, sizeof(buf)) != PSM_OK)
        return env->NewStringUTF("");
    return env->NewStringUTF(buf);
}

JNIEXPORT jlong JNICALL JNI_FN(nativeEstimateMemory)(JNIEnv *, jclass, jlong h)
{
    return static_cast<jlong>(psm_estimate_slice_memory(sess(h)));
}

/* --- Viewport --------------------------------------------------------- */
/*
 * Diese Aufrufe kommen vom GL-Thread des GLSurfaceView, nicht vom
 * UI-Thread. Pro Bild geht genau ein Aufruf hinunter (nativeVpRender) -
 * Geometrie wandert nie durch JNI, die liest der Viewport direkt aus der
 * Session. Siehe docs/entscheidungen.md, E-03.
 */

#define JNI_VP(name) Java_de_psmobile_core_PsmViewport_##name

static inline psm_viewport *vp(jlong h) { return reinterpret_cast<psm_viewport *>(h); }

JNIEXPORT jlong JNICALL JNI_VP(nativeCreate)(JNIEnv *env, jclass, jlong session, jstring shaderDir)
{
    const std::string dir = jstr(env, shaderDir);
    return reinterpret_cast<jlong>(psm_viewport_create(sess(session), dir.c_str()));
}

JNIEXPORT void JNICALL JNI_VP(nativeDestroy)(JNIEnv *, jclass, jlong h)
{
    psm_viewport_destroy(vp(h));
}

JNIEXPORT void JNICALL JNI_VP(nativeResize)(JNIEnv *, jclass, jlong h, jint w, jint hgt)
{
    psm_viewport_resize(vp(h), w, hgt);
}

JNIEXPORT void JNICALL JNI_VP(nativeRender)(JNIEnv *, jclass, jlong h)
{
    psm_viewport_render(vp(h));
}

JNIEXPORT void JNICALL JNI_VP(nativeInvalidate)(JNIEnv *, jclass, jlong h)
{
    psm_viewport_invalidate(vp(h));
}

JNIEXPORT void JNICALL JNI_VP(nativeOrbit)(JNIEnv *, jclass, jlong h, jfloat dx, jfloat dy)
{
    psm_viewport_orbit(vp(h), dx, dy);
}

JNIEXPORT void JNICALL JNI_VP(nativePan)(JNIEnv *, jclass, jlong h, jfloat dx, jfloat dy)
{
    psm_viewport_pan(vp(h), dx, dy);
}

JNIEXPORT void JNICALL JNI_VP(nativeZoom)(JNIEnv *, jclass, jlong h, jfloat factor)
{
    psm_viewport_zoom(vp(h), factor);
}

JNIEXPORT void JNICALL JNI_VP(nativeResetView)(JNIEnv *, jclass, jlong h)
{
    psm_viewport_reset_view(vp(h));
}

JNIEXPORT void JNICALL JNI_VP(nativeViewPreset)(JNIEnv *, jclass, jlong h, jint which)
{
    psm_viewport_view_preset(vp(h), which);
}

JNIEXPORT jint JNICALL JNI_VP(nativePick)(JNIEnv *, jclass, jlong h, jfloat x, jfloat y)
{
    return psm_viewport_pick(vp(h), x, y);
}

JNIEXPORT jint JNICALL JNI_VP(nativeDragSelected)(JNIEnv *, jclass, jlong h,
                                                  jfloat fx, jfloat fy,
                                                  jfloat tx, jfloat ty)
{
    return psm_viewport_drag_selected(vp(h), fx, fy, tx, ty);
}

JNIEXPORT void JNICALL JNI_VP(nativeSetSelection)(JNIEnv *, jclass, jlong h, jint id)
{
    psm_viewport_set_selection(vp(h), id);
}

JNIEXPORT void JNICALL JNI_VP(nativeSetSelections)(
    JNIEnv *env, jclass, jlong h, jintArray ids, jint primary)
{
    if (ids == nullptr) {
        psm_viewport_set_selections(vp(h), nullptr, 0, primary);
        return;
    }
    const jsize count = env->GetArrayLength(ids);
    std::vector<jint> values(static_cast<size_t>(count));
    env->GetIntArrayRegion(ids, 0, count, values.data());
    psm_viewport_set_selections(
        vp(h), reinterpret_cast<const psm_object_id *>(values.data()),
        values.size(), primary);
}

JNIEXPORT jstring JNICALL JNI_VP(nativeSurfacePick)(
    JNIEnv *env, jclass, jlong h, jfloat x, jfloat y)
{
    psm_surface_hit hit{};
    if (! psm_viewport_pick_surface(vp(h), x, y, &hit))
        return nullptr;
    const std::string value =
        std::to_string(hit.object_id) + "\t" +
        std::to_string(hit.volume_index) + "\t" +
        std::to_string(hit.facet_index) + "\t" +
        std::to_string(hit.instance_index) + "\t" +
        std::to_string(hit.position[0]) + "\t" +
        std::to_string(hit.position[1]) + "\t" +
        std::to_string(hit.position[2]) + "\t" +
        std::to_string(hit.normal[0]) + "\t" +
        std::to_string(hit.normal[1]) + "\t" +
        std::to_string(hit.normal[2]);
    return env->NewStringUTF(value.c_str());
}

/*
 * G-Code-Vorschau. nativeLoadPreview muss auf dem GL-Thread laufen, weil
 * libvgcode dabei seine Puffer und Shader anlegt.
 */

JNIEXPORT jint JNICALL JNI_VP(nativeScaleSelected)(JNIEnv *, jclass, jlong h, jfloat factor)
{
    return psm_viewport_scale_selected(vp(h), factor);
}

/* --- Griffe --------------------------------------------------------- */

JNIEXPORT void JNICALL JNI_VP(nativeSetGizmo)(JNIEnv *, jclass, jlong h, jint mode)
{
    psm_viewport_set_gizmo(vp(h), static_cast<psm_gizmo_mode>(mode));
}

JNIEXPORT jint JNICALL JNI_VP(nativeGizmoPick)(JNIEnv *, jclass, jlong h,
                                               jfloat x, jfloat y, jfloat radius)
{
    return psm_viewport_gizmo_pick(vp(h), x, y, radius);
}

JNIEXPORT jint JNICALL JNI_VP(nativeGizmoDrag)(JNIEnv *, jclass, jlong h, jint axis,
                                               jfloat fx, jfloat fy,
                                               jfloat tx, jfloat ty, jint snap)
{
    return psm_viewport_gizmo_drag(vp(h), axis, fx, fy, tx, ty, snap);
}

JNIEXPORT void JNICALL JNI_VP(nativeSetMode)(JNIEnv *, jclass, jlong h, jint mode)
{
    psm_viewport_set_mode(vp(h), static_cast<psm_view_mode>(mode));
}

JNIEXPORT jint JNICALL JNI_VP(nativeGetMode)(JNIEnv *, jclass, jlong h)
{
    return static_cast<jint>(psm_viewport_get_mode(vp(h)));
}

JNIEXPORT jint JNICALL JNI_VP(nativeLoadPreview)(JNIEnv *, jclass, jlong h)
{
    return psm_viewport_load_preview(vp(h));
}

JNIEXPORT jint JNICALL JNI_VP(nativeLayerCount)(JNIEnv *, jclass, jlong h)
{
    return psm_viewport_layer_count(vp(h));
}

JNIEXPORT void JNICALL JNI_VP(nativeSetLayerRange)(JNIEnv *, jclass, jlong h, jint lo, jint hi)
{
    psm_viewport_set_layer_range(vp(h), lo, hi);
}

JNIEXPORT jstring JNICALL JNI_VP(nativeLastError)(JNIEnv *env, jclass, jlong h)
{
    return env->NewStringUTF(psm_viewport_last_error(vp(h)));
}

} /* extern "C" */

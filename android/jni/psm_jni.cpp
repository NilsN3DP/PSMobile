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

#include <cstring>
#include <string>

#include "psmobile_core.h"

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

JNIEXPORT jint JNICALL JNI_FN(nativeDuplicate)(JNIEnv *, jclass, jlong h, jint id)
{
    psm_object_id nid = PSM_INVALID_ID;
    if (psm_model_duplicate(sess(h), id, &nid) != PSM_OK)
        return PSM_INVALID_ID;
    return nid;
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
    char buf[4096] = { 0 };
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

JNIEXPORT jlong JNICALL JNI_FN(nativeEstimateMemory)(JNIEnv *, jclass, jlong h)
{
    return static_cast<jlong>(psm_estimate_slice_memory(sess(h)));
}

} /* extern "C" */

/*
 * aoba_ime.c — Wayland text-input-v3 bridge for Minecraft (GLFW) on Linux.
 *
 * Reuses GLFW's existing wl_display + wl_surface, binds its own wl_seat and
 * zwp_text_input_manager_v3 from the registry on a private event queue, and
 * forwards preedit/commit events to the Java side (io.legenlee.ime.WaylandIme).
 *
 * The private queue means we never steal GLFW's default-queue events: GLFW's
 * glfwPollEvents() reads the socket (distributing events to all queues), and we
 * drain our queue from Java via nDispatch() on the main thread each frame.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <wayland-client.h>
#include "text-input-unstable-v3-client-protocol.h"

static JavaVM   *g_vm        = NULL;
static jclass    g_cls       = NULL;   /* global ref to WaylandIme */
static jmethodID m_onCommit  = NULL;   /* (Ljava/lang/String;)V   */
static jmethodID m_onPreedit = NULL;   /* (Ljava/lang/String;II)V */
static jmethodID m_onEnter   = NULL;   /* ()V */
static jmethodID m_onLeave   = NULL;   /* ()V */

static struct wl_display     *g_display  = NULL;
static struct wl_surface     *g_surface  = NULL;   /* GLFW window surface (focus match) */
static struct wl_event_queue *g_queue    = NULL;   /* private queue */
static struct wl_registry    *g_registry = NULL;
static struct wl_seat        *g_seat     = NULL;
static struct zwp_text_input_manager_v3 *g_tim = NULL;
static struct zwp_text_input_v3         *g_ti  = NULL;

static int   g_focused = 0;

/* state accumulated between 'done' events (text-input-v3 is double-buffered) */
static char *g_pending_preedit = NULL;
static int32_t g_pre_begin = 0, g_pre_end = 0;
static char *g_pending_commit  = NULL;

static JNIEnv *get_env(void) {
    JNIEnv *env = NULL;
    if (!g_vm) return NULL;
    if ((*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) return env;
    if ((*g_vm)->AttachCurrentThread(g_vm, (void **)&env, NULL) == 0) return env;
    return NULL;
}

static void clear_exc(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

static char *dupstr(const char *s) { return s ? strdup(s) : NULL; }
static void  freestr(char **p) { if (*p) { free(*p); *p = NULL; } }

/* ----------------------- zwp_text_input_v3 listener ----------------------- */

static void ti_enter(void *d, struct zwp_text_input_v3 *ti, struct wl_surface *surface) {
    (void)d; (void)ti;
    if (surface != g_surface) return;
    g_focused = 1;
    JNIEnv *e = get_env();
    if (e) { (*e)->CallStaticVoidMethod(e, g_cls, m_onEnter); clear_exc(e); }
}

static void ti_leave(void *d, struct zwp_text_input_v3 *ti, struct wl_surface *surface) {
    (void)d; (void)ti;
    if (surface != g_surface) return;
    g_focused = 0;
    JNIEnv *e = get_env();
    if (e) { (*e)->CallStaticVoidMethod(e, g_cls, m_onLeave); clear_exc(e); }
}

static void ti_preedit(void *d, struct zwp_text_input_v3 *ti,
                       const char *text, int32_t begin, int32_t end) {
    (void)d; (void)ti;
    freestr(&g_pending_preedit);
    g_pending_preedit = dupstr(text);
    g_pre_begin = begin;
    g_pre_end   = end;
}

static void ti_commit(void *d, struct zwp_text_input_v3 *ti, const char *text) {
    (void)d; (void)ti;
    freestr(&g_pending_commit);
    g_pending_commit = dupstr(text);
}

static void ti_delete(void *d, struct zwp_text_input_v3 *ti,
                      uint32_t before_length, uint32_t after_length) {
    (void)d; (void)ti; (void)before_length; (void)after_length;
    /* TODO: surrounding-text deletion (forward to Java when wiring EditBox). */
}

static void ti_done(void *d, struct zwp_text_input_v3 *ti, uint32_t serial) {
    (void)d; (void)ti; (void)serial;
    JNIEnv *e = get_env();
    if (!e) return;

    if (g_pending_commit) {
        jstring s = (*e)->NewStringUTF(e, g_pending_commit);
        (*e)->CallStaticVoidMethod(e, g_cls, m_onCommit, s);
        clear_exc(e);
        (*e)->DeleteLocalRef(e, s);
        freestr(&g_pending_commit);
    }

    /* Always report preedit so the field can update (empty == composition cleared). */
    jstring ps = (*e)->NewStringUTF(e, g_pending_preedit ? g_pending_preedit : "");
    (*e)->CallStaticVoidMethod(e, g_cls, m_onPreedit, ps, (jint)g_pre_begin, (jint)g_pre_end);
    clear_exc(e);
    (*e)->DeleteLocalRef(e, ps);
}

static const struct zwp_text_input_v3_listener ti_listener = {
    .enter                   = ti_enter,
    .leave                   = ti_leave,
    .preedit_string          = ti_preedit,
    .commit_string           = ti_commit,
    .delete_surrounding_text = ti_delete,
    .done                    = ti_done,
};

/* ------------------------------- registry -------------------------------- */

static void reg_global(void *d, struct wl_registry *reg,
                       uint32_t name, const char *iface, uint32_t version) {
    (void)d;
    if (strcmp(iface, "wl_seat") == 0 && !g_seat) {
        uint32_t v = version > 7 ? 7 : version;
        g_seat = wl_registry_bind(reg, name, &wl_seat_interface, v);
    } else if (strcmp(iface, zwp_text_input_manager_v3_interface.name) == 0 && !g_tim) {
        g_tim = wl_registry_bind(reg, name, &zwp_text_input_manager_v3_interface, 1);
    }
}

static void reg_remove(void *d, struct wl_registry *reg, uint32_t name) {
    (void)d; (void)reg; (void)name;
}

static const struct wl_registry_listener reg_listener = {
    .global        = reg_global,
    .global_remove = reg_remove,
};

/* --------------------------------- JNI ----------------------------------- */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;
    return JNI_VERSION_1_6;
}

/*
 * nInit: capture GLFW's display/surface, bind seat + text-input-manager-v3 on a
 * private queue, and create the text-input object. Returns false if the
 * compositor does not advertise zwp_text_input_manager_v3 (no IME bridge).
 */
JNIEXPORT jboolean JNICALL
Java_io_legenlee_ime_WaylandIme_nInit(JNIEnv *env, jclass cls,
                                      jlong display, jlong surface) {
    g_display = (struct wl_display *)(intptr_t)display;
    g_surface = (struct wl_surface *)(intptr_t)surface;
    if (!g_display || !g_surface) return JNI_FALSE;

    g_cls       = (*env)->NewGlobalRef(env, cls);
    m_onCommit  = (*env)->GetStaticMethodID(env, cls, "onCommit",  "(Ljava/lang/String;)V");
    m_onPreedit = (*env)->GetStaticMethodID(env, cls, "onPreedit", "(Ljava/lang/String;II)V");
    m_onEnter   = (*env)->GetStaticMethodID(env, cls, "onEnter",   "()V");
    m_onLeave   = (*env)->GetStaticMethodID(env, cls, "onLeave",   "()V");
    if (!m_onCommit || !m_onPreedit || !m_onEnter || !m_onLeave) return JNI_FALSE;

    g_queue    = wl_display_create_queue(g_display);
    g_registry = wl_display_get_registry(g_display);
    /* Put the registry (and everything it binds) on our private queue. */
    wl_proxy_set_queue((struct wl_proxy *)g_registry, g_queue);
    wl_registry_add_listener(g_registry, &reg_listener, NULL);

    /* Block until globals are delivered to our queue (main thread, init time). */
    wl_display_roundtrip_queue(g_display, g_queue);

    if (!g_tim || !g_seat) return JNI_FALSE;  /* compositor lacks text-input-v3 */

    g_ti = zwp_text_input_manager_v3_get_text_input(g_tim, g_seat);
    zwp_text_input_v3_add_listener(g_ti, &ti_listener, NULL);
    wl_display_flush(g_display);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_io_legenlee_ime_WaylandIme_nEnable(JNIEnv *env, jclass cls,
                                        jint x, jint y, jint w, jint h) {
    (void)env; (void)cls;
    if (!g_ti) return;
    zwp_text_input_v3_enable(g_ti);
    zwp_text_input_v3_set_content_type(g_ti,
        ZWP_TEXT_INPUT_V3_CONTENT_HINT_NONE,
        ZWP_TEXT_INPUT_V3_CONTENT_PURPOSE_NORMAL);
    zwp_text_input_v3_set_cursor_rectangle(g_ti, x, y, w, h);
    zwp_text_input_v3_commit(g_ti);
    wl_display_flush(g_display);
}

JNIEXPORT void JNICALL
Java_io_legenlee_ime_WaylandIme_nSetCursorRect(JNIEnv *env, jclass cls,
                                               jint x, jint y, jint w, jint h) {
    (void)env; (void)cls;
    if (!g_ti || !g_focused) return;
    zwp_text_input_v3_set_cursor_rectangle(g_ti, x, y, w, h);
    zwp_text_input_v3_commit(g_ti);
    wl_display_flush(g_display);
}

JNIEXPORT void JNICALL
Java_io_legenlee_ime_WaylandIme_nDisable(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    if (!g_ti) return;
    zwp_text_input_v3_disable(g_ti);
    zwp_text_input_v3_commit(g_ti);
    wl_display_flush(g_display);
}

/* Drain our private queue. Call once per frame on the main thread (after
 * glfwPollEvents has read the socket). */
JNIEXPORT void JNICALL
Java_io_legenlee_ime_WaylandIme_nDispatch(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    if (!g_display || !g_queue) return;
    wl_display_dispatch_queue_pending(g_display, g_queue);
}

JNIEXPORT void JNICALL
Java_io_legenlee_ime_WaylandIme_nDestroy(JNIEnv *env, jclass cls) {
    if (g_ti)  { zwp_text_input_v3_destroy(g_ti);  g_ti  = NULL; }
    if (g_tim) { zwp_text_input_manager_v3_destroy(g_tim); g_tim = NULL; }
    if (g_seat)     { wl_seat_destroy(g_seat);         g_seat = NULL; }
    if (g_registry) { wl_registry_destroy(g_registry); g_registry = NULL; }
    if (g_queue)    { wl_event_queue_destroy(g_queue);  g_queue = NULL; }
    if (g_display)  { wl_display_flush(g_display); }
    freestr(&g_pending_preedit);
    freestr(&g_pending_commit);
    if (g_cls) { (*env)->DeleteGlobalRef(env, g_cls); g_cls = NULL; }
    (void)cls;
}

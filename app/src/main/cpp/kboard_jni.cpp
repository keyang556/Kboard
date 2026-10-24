// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Kboard - JNI bridge between the input method and libchewing.
 *
 * Every function takes the ChewingContext pointer that open() returned, as a jlong, and
 * does nothing (returning an empty or failing value) when it is 0. All calls come from
 * the main thread of the input method; a ChewingContext is not thread-safe.
 *
 * Nothing here logs what the user types.
 */

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include <android/log.h>

#include "chewing.h"

namespace {

constexpr const char *kLogTag = "KboardJni";

// Keys other than characters, as numbered by ChewingNative.kt.
enum SpecialKey : jint {
    kSpace = 1,
    kEnter = 2,
    kBackspace = 3,
    kEsc = 4,
    kDelete = 5,
    kLeft = 6,
    kRight = 7,
    kUp = 8,
    kDown = 9,
    kHome = 10,
    kEnd = 11,
    kPageUp = 12,
    kPageDown = 13,
    kTab = 14,
};

// What a key press did, as a bit set; the same bits as libchewing's KEYSTROKE_* constants.
constexpr jint kStatusIgnore = 1;
constexpr jint kStatusCommit = 2;
constexpr jint kStatusAbsorb = 8;

// A safety net against a runaway loop over candidate lists.
constexpr int kMaxCandidateLists = 16;

ChewingContext *asContext(jlong pointer) {
    return reinterpret_cast<ChewingContext *>(static_cast<intptr_t>(pointer));
}

/*
 * libchewing speaks standard UTF-8, while JNI's NewStringUTF and GetStringUTFChars use
 * "modified" UTF-8, which encodes characters outside the Basic Multilingual Plane (emoji,
 * which libchewing's symbol table has plenty of) differently. So strings are transcoded
 * here, to and from the UTF-16 that NewString and GetStringChars use.
 */
jstring toJavaString(JNIEnv *env, const char *utf8) {
    std::vector<jchar> utf16;
    if (utf8 != nullptr) {
        const auto *bytes = reinterpret_cast<const unsigned char *>(utf8);
        while (*bytes != 0) {
            uint32_t codePoint;
            int continuationBytes;
            const unsigned char lead = *bytes++;
            if (lead < 0x80) {
                codePoint = lead;
                continuationBytes = 0;
            } else if ((lead & 0xE0) == 0xC0) {
                codePoint = lead & 0x1F;
                continuationBytes = 1;
            } else if ((lead & 0xF0) == 0xE0) {
                codePoint = lead & 0x0F;
                continuationBytes = 2;
            } else if ((lead & 0xF8) == 0xF0) {
                codePoint = lead & 0x07;
                continuationBytes = 3;
            } else {
                codePoint = 0xFFFD;
                continuationBytes = 0;
            }
            for (int i = 0; i < continuationBytes; i++) {
                if ((*bytes & 0xC0) != 0x80) {
                    codePoint = 0xFFFD;
                    break;
                }
                codePoint = (codePoint << 6) | (*bytes++ & 0x3F);
            }
            if (codePoint > 0x10FFFF || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
                codePoint = 0xFFFD;
            }
            if (codePoint >= 0x10000) {
                codePoint -= 0x10000;
                utf16.push_back(static_cast<jchar>(0xD800 | (codePoint >> 10)));
                utf16.push_back(static_cast<jchar>(0xDC00 | (codePoint & 0x3FF)));
            } else {
                utf16.push_back(static_cast<jchar>(codePoint));
            }
        }
    }
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

std::string toUtf8(JNIEnv *env, jstring string) {
    std::string utf8;
    if (string == nullptr) return utf8;
    const jsize length = env->GetStringLength(string);
    const jchar *utf16 = env->GetStringChars(string, nullptr);
    if (utf16 == nullptr) return utf8;
    utf8.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; i++) {
        uint32_t codePoint = utf16[i];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && i + 1 < length) {
            const jchar low = utf16[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (low - 0xDC00);
                i++;
            }
        }
        if (codePoint < 0x80) {
            utf8.push_back(static_cast<char>(codePoint));
        } else if (codePoint < 0x800) {
            utf8.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            utf8.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint < 0x10000) {
            utf8.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            utf8.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            utf8.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            utf8.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            utf8.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            utf8.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            utf8.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }
    env->ReleaseStringChars(string, utf16);
    return utf8;
}

jobjectArray toJavaStringArray(JNIEnv *env, const std::vector<std::string> &strings) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(strings.size()), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (array == nullptr) return nullptr;
    for (size_t i = 0; i < strings.size(); i++) {
        jstring element = toJavaString(env, strings[i].c_str());
        if (element == nullptr) return nullptr;
        env->SetObjectArrayElement(array, static_cast<jsize>(i), element);
        env->DeleteLocalRef(element);
    }
    return array;
}

jint keystrokeStatus(ChewingContext *context) {
    jint status = 0;
    if (chewing_keystroke_CheckIgnore(context)) status |= kStatusIgnore;
    if (chewing_commit_Check(context)) status |= kStatusCommit;
    if (chewing_keystroke_CheckAbsorb(context)) status |= kStatusAbsorb;
    return status;
}

// The candidates of the open candidate list, from its current page on.
std::vector<std::string> enumerateCandidates(ChewingContext *context) {
    std::vector<std::string> candidates;
    chewing_cand_Enumerate(context);
    while (chewing_cand_hasNext(context)) {
        const char *candidate = chewing_cand_String_static(context);
        candidates.emplace_back(candidate != nullptr ? candidate : "");
    }
    return candidates;
}

bool isSelecting(ChewingContext *context) {
    return chewing_cand_TotalChoice(context) > 0;
}

}  // namespace

#define KBOARD_JNI(returnType, name) \
    extern "C" JNIEXPORT returnType JNICALL Java_io_github_keyang556_kboard_engine_ChewingNative_##name

KBOARD_JNI(jlong, open)(JNIEnv *env, jclass, jstring systemDir, jstring userDictionary) {
    const std::string systemPath = toUtf8(env, systemDir);
    const std::string userPath = toUtf8(env, userDictionary);
    if (systemPath.empty() || userPath.empty()) return 0;
    ChewingContext *context = chewing_new2(systemPath.c_str(), userPath.c_str(), nullptr, nullptr);
    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "chewing_new2 failed");
        return 0;
    }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(context));
}

KBOARD_JNI(void, close)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context != nullptr) chewing_delete(context);
}

KBOARD_JNI(jint, setKeyboardType)(JNIEnv *, jclass, jlong pointer, jint type) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return -1;
    return chewing_set_KBType(context, type);
}

KBOARD_JNI(jint, configSetInt)(JNIEnv *env, jclass, jlong pointer, jstring name, jint value) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return -1;
    const std::string option = toUtf8(env, name);
    return chewing_config_set_int(context, option.c_str(), value);
}

KBOARD_JNI(jint, handleKey)(JNIEnv *, jclass, jlong pointer, jint key) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return kStatusIgnore;
    chewing_handle_Default(context, key);
    return keystrokeStatus(context);
}

KBOARD_JNI(jint, handleSpecial)(JNIEnv *, jclass, jlong pointer, jint key) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return kStatusIgnore;
    switch (key) {
        case kSpace: chewing_handle_Space(context); break;
        case kEnter: chewing_handle_Enter(context); break;
        case kBackspace: chewing_handle_Backspace(context); break;
        case kEsc: chewing_handle_Esc(context); break;
        case kDelete: chewing_handle_Del(context); break;
        case kLeft: chewing_handle_Left(context); break;
        case kRight: chewing_handle_Right(context); break;
        case kUp: chewing_handle_Up(context); break;
        case kDown: chewing_handle_Down(context); break;
        case kHome: chewing_handle_Home(context); break;
        case kEnd: chewing_handle_End(context); break;
        case kPageUp: chewing_handle_PageUp(context); break;
        case kPageDown: chewing_handle_PageDown(context); break;
        case kTab: chewing_handle_Tab(context); break;
        default: return kStatusIgnore;
    }
    return keystrokeStatus(context);
}

/* Takes what libchewing has to commit, emptying its commit buffer so it is taken only once. */
KBOARD_JNI(jstring, takeCommitString)(JNIEnv *env, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr || !chewing_commit_Check(context)) return toJavaString(env, "");
    jstring commit = toJavaString(env, chewing_commit_String_static(context));
    chewing_ack(context);
    return commit;
}

KBOARD_JNI(jstring, preeditString)(JNIEnv *env, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return toJavaString(env, "");
    return toJavaString(env, chewing_buffer_String_static(context));
}

KBOARD_JNI(jstring, bopomofoString)(JNIEnv *env, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return toJavaString(env, "");
    return toJavaString(env, chewing_bopomofo_String_static(context));
}

KBOARD_JNI(jint, cursor)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return 0;
    return chewing_cursor_Current(context);
}

KBOARD_JNI(jint, commitPreedit)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return -1;
    return chewing_commit_preedit_buf(context);
}

KBOARD_JNI(void, clear)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return;
    chewing_cand_close(context);
    chewing_clean_bopomofo_buf(context);
    chewing_clean_preedit_buf(context);
}

KBOARD_JNI(jboolean, isSelecting)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    return context != nullptr && isSelecting(context) ? JNI_TRUE : JNI_FALSE;
}

KBOARD_JNI(jint, openCandidates)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return -1;
    return chewing_cand_open(context);
}

KBOARD_JNI(jint, closeCandidates)(JNIEnv *, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr) return -1;
    return chewing_cand_close(context);
}

KBOARD_JNI(jobjectArray, candidates)(JNIEnv *env, jclass, jlong pointer) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr || !isSelecting(context)) return toJavaStringArray(env, {});
    return toJavaStringArray(env, enumerateCandidates(context));
}

KBOARD_JNI(jint, chooseCandidate)(JNIEnv *, jclass, jlong pointer, jint index) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr || index < 0) return -1;
    return chewing_cand_choose_by_index(context, index);
}

/*
 * Every candidate list for the phrase ending at the cursor, longest phrases first, without
 * leaving libchewing in its selection state: what the candidate bar shows while typing.
 */
KBOARD_JNI(jobjectArray, peekCandidateLists)(JNIEnv *env, jclass, jlong pointer) {
    jclass arrayClass = env->FindClass("[Ljava/lang/String;");
    if (arrayClass == nullptr) return nullptr;
    std::vector<std::vector<std::string>> lists;
    ChewingContext *context = asContext(pointer);
    if (context != nullptr && !isSelecting(context) && chewing_cand_open(context) == 0) {
        chewing_cand_list_first(context);
        while (true) {
            lists.push_back(enumerateCandidates(context));
            if (lists.size() >= kMaxCandidateLists || !chewing_cand_list_has_next(context)) break;
            if (chewing_cand_list_next(context) != 0) break;
        }
        chewing_cand_close(context);
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(lists.size()), arrayClass, nullptr);
    env->DeleteLocalRef(arrayClass);
    if (result == nullptr) return nullptr;
    for (size_t i = 0; i < lists.size(); i++) {
        jobjectArray list = toJavaStringArray(env, lists[i]);
        if (list == nullptr) return nullptr;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), list);
        env->DeleteLocalRef(list);
    }
    return result;
}

/* Chooses candidate [index] of candidate list [list], as peekCandidateLists numbered them. */
KBOARD_JNI(jint, chooseFromList)(JNIEnv *, jclass, jlong pointer, jint list, jint index) {
    ChewingContext *context = asContext(pointer);
    if (context == nullptr || list < 0 || index < 0) return -1;
    if (isSelecting(context) || chewing_cand_open(context) != 0) return -1;
    chewing_cand_list_first(context);
    for (jint i = 0; i < list; i++) {
        if (chewing_cand_list_next(context) != 0) {
            chewing_cand_close(context);
            return -1;
        }
    }
    const jint result = chewing_cand_choose_by_index(context, index);
    if (isSelecting(context)) chewing_cand_close(context);
    return result;
}

KBOARD_JNI(jstring, version)(JNIEnv *env, jclass) {
    return toJavaString(env, chewing_version());
}

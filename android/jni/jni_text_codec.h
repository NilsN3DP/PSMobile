#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace psm_jni_text {
inline std::string truncate_utf8(const std::string &value, size_t maxBytes) {
    size_t n = value.size() < maxBytes ? value.size() : maxBytes;
    while (n > 0 && (static_cast<unsigned char>(value[n]) & 0xC0) == 0x80) --n;
    return value.substr(0, n);
}
inline bool utf16_to_utf8(const uint16_t *in, size_t length, std::string &out) {
    out.clear();
    for (size_t i = 0; i < length; ++i) {
        uint32_t cp = in[i];
        if (cp >= 0xD800 && cp <= 0xDBFF) {
            if (i + 1 >= length || in[i + 1] < 0xDC00 || in[i + 1] > 0xDFFF) { out.clear(); return false; }
            cp = 0x10000u + ((cp - 0xD800u) << 10) + (in[++i] - 0xDC00u);
        } else if (cp >= 0xDC00 && cp <= 0xDFFF) { out.clear(); return false; }
        if (cp <= 0x7F) out.push_back(char(cp));
        else if (cp <= 0x7FF) { out.push_back(char(0xC0 | (cp >> 6))); out.push_back(char(0x80 | (cp & 63))); }
        else if (cp <= 0xFFFF) { out.push_back(char(0xE0 | (cp >> 12))); out.push_back(char(0x80 | ((cp >> 6) & 63))); out.push_back(char(0x80 | (cp & 63))); }
        else { out.push_back(char(0xF0 | (cp >> 18))); out.push_back(char(0x80 | ((cp >> 12) & 63))); out.push_back(char(0x80 | ((cp >> 6) & 63))); out.push_back(char(0x80 | (cp & 63))); }
    }
    return true;
}
}

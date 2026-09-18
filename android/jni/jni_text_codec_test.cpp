#include "jni_text_codec.h"
#include <cassert>
int main() {
    std::string out;
    const uint16_t emoji[] = { 'A', 0xD83D, 0xDD20 };
    assert(psm_jni_text::utf16_to_utf8(emoji, 3, out) && out == "A🔠");
    const uint16_t bad[] = { 'x', 0xD83D };
    assert(!psm_jni_text::utf16_to_utf8(bad, 2, out) && out.empty());
    assert(psm_jni_text::truncate_utf8("A🔠B", 2) == "A");
    return 0;
}

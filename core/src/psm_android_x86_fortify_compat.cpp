// GMP 6.3 emits __fprintf_chk references with the Android x86_64 toolchain.
// Bionic API 26 does not export that glibc fortify entry point. This bridge is
// linked only into the Android x86_64 runtime; all other targets retain their
// native libc implementation.
#include <cstdarg>
#include <cstdio>

extern "C" int __fprintf_chk(FILE* stream, int /*flag*/, const char* format, ...)
{
    va_list arguments;
    va_start(arguments, format);
    const int written = vfprintf(stream, format, arguments);
    va_end(arguments);
    return written;
}

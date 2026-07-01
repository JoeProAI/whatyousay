# Native build for the on-device machine-translation engine.
#
# Produces libwhatyousay_llama.so from llama.cpp plus the JNI bridge in
# llama_jni.cpp. The Kotlin side is ai.whatyousay.engine.LlamaTranslator.
#
# This build is opt-in: the app assembles and runs on the stub pipeline without
# it. Enable it with -PwithNative=true (see app/build.gradle.kts), which is how
# the arm64-v8a library is produced for a real device build.

cmake_minimum_required(VERSION 3.22.1)
project(whatyousay_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pinned llama.cpp release. Keep this in sync with training/export_gguf.py so the
# runtime that runs the GGUF matches the one it was exported with.
set(LLAMA_CPP_TAG "b9688" CACHE STRING "llama.cpp release tag to build against")

# Trim llama.cpp to just the library; we do not ship its tools or server.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)

# The Android Gradle plugin compiles the native code with CMAKE_BUILD_TYPE=Debug
# for a debug-packaged APK. For ggml that means -O0, which makes on-device
# inference roughly 20-50x slower (a short translation can take a minute). Force
# an optimized build so the debug APK we hand testers is actually fast.
if(NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# Give ggml the arm64 CPU math extensions modern phones have. Without an explicit
# -march the NDK targets a bare armv8-a and the quantized matmul falls back to slow
# scalar code. armv8.2-a+dotprod+i8mm+fp16 covers current flagship SoCs (this build
# targets arm64 devices with these features; older cores would need runtime dispatch
# via GGML_CPU_ALL_VARIANTS).
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "" FORCE)
endif()

# Source resolution: a local checkout (set -DLLAMA_CPP_DIR=/path) is preferred for
# offline and reproducible builds; otherwise fetch the pinned tag.
if(DEFINED LLAMA_CPP_DIR AND EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(STATUS "whatyousay: using local llama.cpp at ${LLAMA_CPP_DIR}")
    add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp" EXCLUDE_FROM_ALL)
else()
    message(STATUS "whatyousay: fetching llama.cpp ${LLAMA_CPP_TAG}")
    include(FetchContent)
    FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG ${LLAMA_CPP_TAG}
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(llama_cpp)
endif()

add_library(whatyousay_llama SHARED llama_jni.cpp)

find_library(log-lib log)
target_link_libraries(whatyousay_llama llama ${log-lib})

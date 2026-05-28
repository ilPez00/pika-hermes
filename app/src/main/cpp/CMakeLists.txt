cmake_minimum_required(VERSION 3.22)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
set(LLAMACPP_URL "https://github.com/ggml-org/llama.cpp/archive/refs/tags/b4867.tar.gz")
set(LLAMACPP_SRC_DIR "${CMAKE_BINARY_DIR}/_deps/llama.cpp-src")
if(EXISTS "${CMAKE_SOURCE_DIR}/llama.cpp-local/ggml.h")
    message(STATUS "Using local llama.cpp at ${CMAKE_SOURCE_DIR}/llama.cpp-local")
    FetchContent_Declare(
        llama.cpp
        SOURCE_DIR "${CMAKE_SOURCE_DIR}/llama.cpp-local"
    )
else()
    message(STATUS "Downloading llama.cpp from ${LLAMACPP_URL}")
    FetchContent_Declare(
        llama.cpp
        URL ${LLAMACPP_URL}
        SOURCE_DIR ${LLAMACPP_SRC_DIR}
    )
endif()
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
# Backend flags — set both LLAMA_ (old) and GGML_ (b4xxx+) variants for compat
set(LLAMA_CUDA           OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL          OFF CACHE BOOL "" FORCE)
set(LLAMA_VULKAN         OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE         OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENMP         OFF CACHE BOOL "" FORCE)
set(GGML_CUDA            OFF CACHE BOOL "" FORCE)
set(GGML_METAL           OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN          OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)

# ARM optimizations for Android (arm64-v8a)
# NEON: set both variants; CRC32 is via -march=armv8-a+crc compiler flag (no cmake flag)
set(LLAMA_NEON           ON  CACHE BOOL "" FORCE)
set(GGML_NEON            ON  CACHE BOOL "" FORCE)

# Flash attention: halves memory for long contexts — critical on mobile
set(LLAMA_FLASH_ATTN     ON  CACHE BOOL "" FORCE)
set(GGML_FLASH_ATTN      ON  CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama.cpp)

add_library(llama_jni SHARED llama_jni.cpp)

# -O3 + fast-math: ~20-30% inference speedup on ARM; ARM dotprod for int8 matmul
# -march only valid on arm64-v8a; skip on x86_64 (emulator builds)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(llama_jni PRIVATE
        -O3
        -ffast-math
        -march=armv8-a+crc+dotprod
        -fno-exceptions
    )
else()
    target_compile_options(llama_jni PRIVATE
        -O3
        -ffast-math
        -fno-exceptions
    )
endif()

target_link_libraries(llama_jni PRIVATE llama android log)
target_include_directories(llama_jni PRIVATE ${CMAKE_SOURCE_DIR})

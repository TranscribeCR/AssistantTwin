cmake_minimum_required(VERSION 3.22.1)

project(assistanttwin_llm)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ------------------------------------------------------------------
# llama.cpp build options
# ------------------------------------------------------------------

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# ------------------------------------------------------------------
# Build llama.cpp
# ------------------------------------------------------------------

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/llama_cpp_build
)

# ------------------------------------------------------------------
# JNI bridge
# ------------------------------------------------------------------

add_library(
    assistanttwin_llm
    SHARED
    llama_bridge.cpp
)

# ------------------------------------------------------------------
# Include directories
# ------------------------------------------------------------------

target_include_directories(
    assistanttwin_llm
    PRIVATE

    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

# ------------------------------------------------------------------
# Link llama.cpp
# ------------------------------------------------------------------

target_link_libraries(
    assistanttwin_llm

    llama

    android
    log
)

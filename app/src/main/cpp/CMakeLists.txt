cmake_minimum_required(VERSION 3.22.1)
project(assistanttwin_llm)

# llama.cpp is NOT vendored here (it's a large, fast-moving repo). Before building,
# from the project root run:
#   git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
#   git submodule update --init --recursive
# This CMake file then builds llama.cpp's own CMakeLists as a subdirectory and links
# our thin JNI bridge (llama_bridge.cpp) against it.

set(CMAKE_CXX_STANDARD 17)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama_cpp_build)

add_library(assistanttwin_llm SHARED
    llama_bridge.cpp
)

target_link_libraries(assistanttwin_llm
    llama
    common
    android
    log
)

target_include_directories(assistanttwin_llm PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

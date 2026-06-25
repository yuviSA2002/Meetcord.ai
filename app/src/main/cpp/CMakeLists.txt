cmake_minimum_required(VERSION 3.22.1)

project("meetcord_native")

# 1. whisper.cpp integration
# In later steps, we will download/submodule whisper.cpp into app/src/main/cpp/whisper.cpp
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Optimize build size
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Only add subdirectory if whisper.cpp has been checked out
if(EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    add_subdirectory(${WHISPER_DIR})
endif()

# 2. Add our JNI library that will bridge Kotlin <-> C++
add_library(
        meetcord_jni
        SHARED
        native-lib.cpp
)

# 3. Include directories
target_include_directories(meetcord_jni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${WHISPER_DIR}/include
        ${WHISPER_DIR}/ggml/include
)

# 4. Link libraries
# Android's log library for __android_log_print
target_link_libraries(
        meetcord_jni
        log
        whisper
)

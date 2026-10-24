# SPDX-License-Identifier: GPL-3.0-or-later
cmake_minimum_required(VERSION 3.27)
project(kboard_jni LANGUAGES C CXX)

# libchewing is linked in statically, and only its library is built: no shared library,
# command line tools, documentation, dictionaries or tests. The dictionaries are compiled
# for the build machine by Gradle (see BuildChewingData in app/build.gradle.kts).
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_DOC OFF CACHE BOOL "" FORCE)
set(BUILD_DATA OFF CACHE BOOL "" FORCE)
set(WITH_SQLITE3 OFF CACHE BOOL "" FORCE)
set(USE_SYSTEM_CHEWING_CLI ON CACHE BOOL "" FORCE)

# Corrosion, the bridge between CMake and cargo that libchewing uses, comes from a git
# submodule, so configuring never downloads anything.
set(FETCHCONTENT_SOURCE_DIR_CORROSION "${CMAKE_CURRENT_SOURCE_DIR}/corrosion" CACHE PATH "" FORCE)

# libchewing names its version after `git describe` of the tree it is built in, which
# would differ between a clone and a source tarball. Its own version is enough.
set(CMAKE_DISABLE_FIND_PACKAGE_Git TRUE)

# The engine is always built optimised, also in debug builds of the app.
set(CMAKE_BUILD_TYPE Release)

# Keep paths of the build machine out of the binaries, so builds are reproducible.
add_compile_options("-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")

add_subdirectory(libchewing EXCLUDE_FROM_ALL)

set(KBOARD_CARGO_HOME "$ENV{CARGO_HOME}")
if(NOT KBOARD_CARGO_HOME)
    set(KBOARD_CARGO_HOME "$ENV{HOME}/.cargo")
endif()
corrosion_add_target_rustflags(chewing_capi
    "--remap-path-prefix=${CMAKE_CURRENT_SOURCE_DIR}/libchewing=/libchewing"
    "--remap-path-prefix=${KBOARD_CARGO_HOME}=/cargo"
)

add_library(kboard_jni SHARED kboard_jni.cpp)
target_compile_features(kboard_jni PRIVATE cxx_std_17)
target_compile_options(kboard_jni PRIVATE -Wall -Wextra -fvisibility=hidden)
target_link_libraries(kboard_jni PRIVATE libchewing log)
target_link_options(kboard_jni PRIVATE
    "LINKER:--gc-sections"
    "LINKER:--exclude-libs,ALL"
)

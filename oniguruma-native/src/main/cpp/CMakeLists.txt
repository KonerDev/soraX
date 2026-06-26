cmake_minimum_required(VERSION 3.22.1)

project("oniguruma-binding")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=. -g0")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=. -g0")

set(BUILD_TEST OFF CACHE BOOL "Disable oniguruma tests" FORCE)
add_subdirectory(oniguruma EXCLUDE_FROM_ALL)

add_library("oniguruma-binding" SHARED binding.cpp)

target_link_libraries("oniguruma-binding" PUBLIC onig)

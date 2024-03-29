# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("mqaidentifier")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             mqaidentifier

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
        mqaid.cpp mqa_identifier.h)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

#find_library( # Sets the name of the path variable.
#        FLAC
        # Specifies the name of the NDK library that
        # you want CMake to locate.
#        ${CMAKE_CURRENT_SOURCE_DIR}/../../jni/flac/include )

#find_library( # Sets the name of the path variable.
              #log-lib log
              #ffmpeg
              # Specifies the name of the NDK library that
              # you want CMake to locate.#
#            )


#add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../jni/libflac/src/libFLAC++)
#add_library(FLAC STATIC ${CMAKE_CURRENT_SOURCE_DIR}/../../jni/libflac/src/libFLAC++/libFLAC++.cc)
#target_include_directories(FLAC INTERFACE ${CMAKE_CURRENT_SOURCE_DIR}/../../jni/flac/include)
#INCLUDE_DIRECTORIES(${CMAKE_CURRENT_SOURCE_DIR}/../../jni/flac/include)
#LINK_DIRECTORIES(${CMAKE_CURRENT_SOURCE_DIR}/../../jni/libflac/src/libFLAC++/)

add_library(
        FLAC++
        SHARED
        IMPORTED)
add_library(
        FLAC
        SHARED
        IMPORTED)
set_target_properties( # Specifies the target library.
        FLAC++
        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        #../../libs/arm64-v8a/libFLAC++.so)
        ${CMAKE_CURRENT_SOURCE_DIR}/../libs/arm64-v8a/libFLAC++.so)
set_target_properties( # Specifies the target library.
        FLAC
        # Specifies the parameter you want to define.
        PROPERTIES IMPORTED_LOCATION

        # Provides the path to the library you want to import.
        #../../libs/arm64-v8a/libFLAC.so)
        ${CMAKE_CURRENT_SOURCE_DIR}/../libs/arm64-v8a/libFLAC.so)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        # Links the target library to the log library
        # included in the NDK.
        #${log-lib}

        mqaidentifier
        FLAC
        FLAC++
        #${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/lib/libFLAC++.so

        #ffmpeg
        )

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../include)
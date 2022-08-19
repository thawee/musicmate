# Install script for directory: /Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "/usr/local")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "/Users/thawee.p/Library/Android/sdk/ndk/25.0.8775105/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-objdump")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib" TYPE STATIC_LIBRARY FILES "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/libogg.a")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/include/ogg" TYPE FILE FILES
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/include/ogg/config_types.h"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/include/ogg/ogg.h"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/include/ogg/os_types.h"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg/OggTargets.cmake")
    file(DIFFERENT EXPORT_FILE_CHANGED FILES
         "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg/OggTargets.cmake"
         "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/CMakeFiles/Export/lib/cmake/Ogg/OggTargets.cmake")
    if(EXPORT_FILE_CHANGED)
      file(GLOB OLD_CONFIG_FILES "$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg/OggTargets-*.cmake")
      if(OLD_CONFIG_FILES)
        message(STATUS "Old export file \"$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg/OggTargets.cmake\" will be replaced.  Removing files [${OLD_CONFIG_FILES}].")
        file(REMOVE ${OLD_CONFIG_FILES})
      endif()
    endif()
  endif()
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg" TYPE FILE FILES "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/CMakeFiles/Export/lib/cmake/Ogg/OggTargets.cmake")
  if("${CMAKE_INSTALL_CONFIG_NAME}" MATCHES "^()$")
    file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg" TYPE FILE FILES "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/CMakeFiles/Export/lib/cmake/Ogg/OggTargets-noconfig.cmake")
  endif()
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/cmake/Ogg" TYPE FILE FILES
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/OggConfig.cmake"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/OggConfigVersion.cmake"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/lib/pkgconfig" TYPE FILE FILES "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/ogg.pc")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/doc/ogg/html" TYPE FILE FILES
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/framing.html"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/index.html"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/oggstream.html"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/ogg-multiplex.html"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/fish_xiph_org.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/multiplex1.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/packets.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/pages.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/stream.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/vorbisword2.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/white-ogg.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/white-xifish.png"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/rfc3533.txt"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/rfc5334.txt"
    "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/skeleton.html"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  file(INSTALL DESTINATION "${CMAKE_INSTALL_PREFIX}/share/doc/ogg/html" TYPE DIRECTORY FILES "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/ogg/doc/libogg")
endif()

if(CMAKE_INSTALL_COMPONENT)
  set(CMAKE_INSTALL_MANIFEST "install_manifest_${CMAKE_INSTALL_COMPONENT}.txt")
else()
  set(CMAKE_INSTALL_MANIFEST "install_manifest.txt")
endif()

string(REPLACE ";" "\n" CMAKE_INSTALL_MANIFEST_CONTENT
       "${CMAKE_INSTALL_MANIFEST_FILES}")
file(WRITE "/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libogg/${CMAKE_INSTALL_MANIFEST}"
     "${CMAKE_INSTALL_MANIFEST_CONTENT}")

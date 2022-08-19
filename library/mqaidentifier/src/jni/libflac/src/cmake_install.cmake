# Install script for directory: /Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/flac/src

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
    set(CMAKE_INSTALL_CONFIG_NAME "Release")
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
  set(CMAKE_OBJDUMP "/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android-objdump")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for each subdirectory.
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/libFLAC/cmake_install.cmake")
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/libFLAC++/cmake_install.cmake")
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/share/replaygain_analysis/cmake_install.cmake")
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/share/replaygain_synthesis/cmake_install.cmake")
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/share/getopt/cmake_install.cmake")
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/share/utf8/cmake_install.cmake")
  include("/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/jni/libflac/src/share/grabbag/cmake_install.cmake")

endif()


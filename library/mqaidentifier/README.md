/Users/thawee.p/Library/Android/sdk/cmake/3.22.1/bin/cmake

cmake \
-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
-DANDROID_ABI=$ABI \
-DANDROID_PLATFORM=android-$MINSDKVERSION \
//Users/thawee.p/Workspaces/Labs/MQA_identifier/flac


//Path to a file.
OGG_INCLUDE_DIR:PATH=/Users/thawee.p/Workspace/musicmate/library/mqaidentifier/src/libogg

//Path to a library.
OGG_LIBRARY:FILEPATH=/Users/thawee.p/Workspace/musicmate/library/mqaidentifier/src/libogg/libogg.a



export NDK=/Users/thawee.p/Library/Android/sdk/ndk/25.0.8775105
export APP_ABI=all
export MINSDKVERSION=30
/Users/thawee.p/Library/Android/sdk/cmake/3.22.1/bin/cmake \
-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
-DANDROID_ABI=$ABI \
-DANDROID_PLATFORM=android-$MINSDKVERSION \
../flac

export NDK=/Users/thawee.p/Library/Android/sdk/ndk/25.0.8775105
export APP_ABI=all
export MINSDKVERSION=30
/Users/thawee.p/Library/Android/sdk/cmake/3.22.1/bin/cmake \
-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
-DANDROID_ABI=$ABI \
-DANDROID_PLATFORM=android-$MINSDKVERSION \
../ogg



/Users/thawee.p/Library/Android/sdk/cmake/3.18.1/bin/cmake \
-H/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/src/main/cpp \
-DCMAKE_SYSTEM_NAME=Android -DCMAKE_EXPORT_COMPILE_COMMANDS=ON -DCMAKE_SYSTEM_VERSION=30 \
-DANDROID_PLATFORM=android-30 -DANDROID_ABI=arm64-v8a -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
-DANDROID_NDK=/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529 \
-DCMAKE_ANDROID_NDK=/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529 \
-DCMAKE_TOOLCHAIN_FILE=/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529/build/cmake/android.toolchain.cmake \
-DCMAKE_MAKE_PROGRAM=/Users/thawee.p/Library/Android/sdk/cmake/3.18.1/bin/ninja \
-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/build/intermediates/cxx/Debug/235h2s1n/obj/arm64-v8a \
-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/build/intermediates/cxx/Debug/235h2s1n/obj/arm64-v8a \
-DCMAKE_BUILD_TYPE=Debug -B/Users/thawee.p/Workspaces/musicmate/library/mqaidentifier/.cxx/Debug/235h2s1n/arm64-v8a -GNinja

/Users/thawee.p/Library/Android/sdk/cmake/3.18.1/bin/cmake \
-DCMAKE_SYSTEM_NAME=Android -DCMAKE_EXPORT_COMPILE_COMMANDS=ON -DCMAKE_SYSTEM_VERSION=30 \
-DANDROID_PLATFORM=android-30 -DANDROID_ABI=arm64-v8a -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
-DANDROID_NDK=/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529 \
-DCMAKE_ANDROID_NDK=/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529 \
-DCMAKE_TOOLCHAIN_FILE=/Users/thawee.p/Library/Android/sdk/ndk/21.4.7075529/build/cmake/android.toolchain.cmake \
../flac
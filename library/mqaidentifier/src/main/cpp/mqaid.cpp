#include <jni.h>
#include <string>
#include <sstream>
#include "mqa_identifier.h"

extern "C" JNIEXPORT jstring JNICALL
Java_apincer_android_mqaidentifier_NativeLib_getMQAInfo(
        JNIEnv* env,
        jobject, /* this */
        jstring pathObj /* flac path*/) {
    const char * path = env->GetStringUTFChars(pathObj, 0 ) ;

    std::stringstream rtss;

    auto id = MQA_identifier(path);
    if (id.detect()) {
        rtss << "MQA";
        if(id.isMQAStudio()) {
            rtss << " Studio";
        }
        rtss << "|" << id.originalSampleRate();
    }
    std::string mqa = rtss.str();
    return env->NewStringUTF(mqa.c_str());
}
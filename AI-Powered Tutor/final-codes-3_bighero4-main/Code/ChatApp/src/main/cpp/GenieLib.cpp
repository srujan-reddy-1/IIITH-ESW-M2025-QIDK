// ---------------------------------------------------------------------
// Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
// ---------------------------------------------------------------------
#include <filesystem>
#include <iostream>
#include <jni.h>
#include <string>

#include "GenieWrapper.hpp"

extern "C" JNIEXPORT jlong JNICALL Java_com_quicinc_chatapp_GenieWrapper_loadModel(JNIEnv *env,
                                                                                   jobject /* this */,
                                                                                   jstring model_dir_path,
                                                                                   jstring htp_config_path)
{

    try
    {
        const char *model_dir_chars = env->GetStringUTFChars(model_dir_path, 0);
        const char *htp_config_chars = env->GetStringUTFChars(htp_config_path, 0);

        std::string model_dir = std::string(model_dir_chars);
        std::string htp_config = std::string(htp_config_chars);

        // Release the UTF chars to prevent memory leaks
        env->ReleaseStringUTFChars(model_dir_path, model_dir_chars);
        env->ReleaseStringUTFChars(htp_config_path, htp_config_chars);

        std::filesystem::path model_config_path = std::filesystem::path(model_dir) / "genie_config.json";
        std::filesystem::path tokenizer_path = std::filesystem::path(model_dir) / "tokenizer.json";

        App::GenieWrapper *chatApp =
            new App::GenieWrapper(model_config_path.string(), model_dir, htp_config, tokenizer_path.string());
        return reinterpret_cast<jlong>(chatApp);
    }
    catch (std::exception &e)
    {
        jclass exception_cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exception_cls, e.what());
        return 0; // Return null pointer on error
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_quicinc_chatapp_GenieWrapper_getResponseForPrompt(JNIEnv *env,
                                                                                             jobject /* this */,
                                                                                             jlong genie_wrapper_handle,
                                                                                             jstring user_question,
                                                                                             jobject callback)
{
    try
    {
        // Get callback method
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onNewStringMethod = env->GetMethodID(callbackClass, "onNewString", "(Ljava/lang/String;)V");

        const char *user_input_chars = env->GetStringUTFChars(user_question, 0);
        std::string user_input = std::string(user_input_chars);

        // Release the UTF chars to prevent memory leaks
        env->ReleaseStringUTFChars(user_question, user_input_chars);

        // Get response from Genie
        App::GenieWrapper *myClass = reinterpret_cast<App::GenieWrapper *>(genie_wrapper_handle);
        auto response = myClass->GetResponseForPrompt(user_input, env, callback, onNewStringMethod);
    }
    catch (std::exception &e)
    {
        jclass exception_cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exception_cls, e.what());
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_quicinc_chatapp_GenieWrapper_freeModel(JNIEnv *env,
                                                                                  jobject /* this */,
                                                                                  jlong genie_wrapper_handle)
{
    try
    {
        App::GenieWrapper *genie_wrapper = reinterpret_cast<App::GenieWrapper *>(genie_wrapper_handle);
        delete genie_wrapper;
    }
    catch (std::exception &e)
    {
        jclass exception_cls = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exception_cls, e.what());
    }
}

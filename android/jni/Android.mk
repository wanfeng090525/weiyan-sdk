# 微验(WY)验证 SDK - Android NDK 构建
# 仅编译 arm64-v8a 架构

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := wyverify
LOCAL_SRC_FILES := weiyan/wyverify.cpp wyverify_jni.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_CPPFLAGS  := -std=c++17 -Wall -O2 -fexceptions -frtti
LOCAL_LDLIBS    := -llog
# 关闭链接器垃圾回收，防止关键加密代码被误删
LOCAL_LDFLAGS   := -Wl,--no-gc-sections
include $(BUILD_SHARED_LIBRARY)

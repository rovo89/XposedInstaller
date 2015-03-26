LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
        android-support-v13 \
        libsuperuser \
        stickylistheaders

LOCAL_PACKAGE_NAME := XposedInstaller
LOCAL_PROGUARD_FLAG_FILES := proguard-project.txt

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
   libsuperuser:libs/libsuperuser-185868.jar \
   stickylistheaders:libs/StickyListHeaders-d7f6fc.jar

include $(BUILD_MULTI_PREBUILT)

include $(call all-makefiles-under,$(LOCAL_PATH))

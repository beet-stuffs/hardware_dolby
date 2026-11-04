#
# Automatically generated file. DO NOT MODIFY
#

PRODUCT_SOONG_NAMESPACES += \
    hardware/dolby

PRODUCT_COPY_FILES += \
    hardware/dolby/proprietary/vendor/etc/dolby/dax-default-spatializer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/dolby/dax-default-spatializer.xml \
    hardware/dolby/proprietary/vendor/etc/dolby/dax-default.xml:$(TARGET_COPY_OUT_VENDOR)/etc/dolby/dax-default.xml \
    hardware/dolby/proprietary/vendor/etc/init/dms-service.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/dms-service.rc \
    hardware/dolby/proprietary/vendor/etc/init/vendor.dolby.media.c2-default-service-dax.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/vendor.dolby.media.c2-default-service-dax.rc \
    hardware/dolby/proprietary/vendor/etc/media_codecs_dolby_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_dolby_audio.xml

PRODUCT_PACKAGES += \
    libcodec2_soft_ac4dec \
    libcodec2_soft_common-dax \
    libcodec2_soft_ddpdec \
    libcodec2_soft_mha1dec \
    libcodec2_soft_mhm1dec \
    libcodec2_store_dolby \
    libdapparamstorage \
    libdeccfg \
    libdlbdsservice \
    libdlbpreg \
    libdmshal \
    libtsr_codec2_store \
    libdlbvolaidl \
    libswdapaidl \
    libswgamedapaidl \
    vendor.dolby.dms-V1-ndk \
    vendor.dolby.hardware.dms@2.0 \
    dms-service.xml \
    vendor.dolby.media.c2-default-service-dax.xml \
    vendor.dolby.dms.service \
    vendor.dolby.media.c2-default-service-dax

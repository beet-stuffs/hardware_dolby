#!/usr/bin/env -S PYTHONPATH=../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import os
from os import path

from extract_utils.fixups_blob import (
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)
from extract_utils.utils import (
    remove_dir_contents,
)

namespace_imports = []


lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
}

blob_fixups: blob_fixups_user_type = {
    (
        'vendor/lib64/soundfx/libswdapaidl.so',
        'vendor/lib64/soundfx/libswgamedapaidl.so',
    ): blob_fixup()
        # DlbGenClass2::check(): replace the prologue (paciasp; stp x29, x30,
        # [sp, #-0x30]!) with mov w0, #1; ret so the Sony brand/model security
        # gate passes on non-Sony devices. The bl before check() differs per
        # library, hence a signature for each (one match each).
        .binary_regex_replace(
            rb'\xff\x43\x06\x91\xbf\x23\x03\xd5\xc0\x03\x5f\xd6\xbf\x0b\x00\x94\x3f\x23\x03\xd5\xfd\x7b\xbd\xa9',
            b'\xff\x43\x06\x91\xbf\x23\x03\xd5\xc0\x03\x5f\xd6\xbf\x0b\x00\x94\x20\x00\x80\x52\xc0\x03\x5f\xd6',
        )
        .binary_regex_replace(
            rb'\xff\x43\x06\x91\xbf\x23\x03\xd5\xc0\x03\x5f\xd6\x05\x22\x00\x94\x3f\x23\x03\xd5\xfd\x7b\xbd\xa9',
            b'\xff\x43\x06\x91\xbf\x23\x03\xd5\xc0\x03\x5f\xd6\x05\x22\x00\x94\x20\x00\x80\x52\xc0\x03\x5f\xd6',
        )
        .replace_needed('android.hardware.audio.effect-V2-ndk.so', 'android.hardware.audio.effect-V3-ndk.so')
        .replace_needed('android.media.audio.common.types-V3-ndk.so', 'android.media.audio.common.types-V4-ndk.so')
        .replace_needed('libaudio_aidl_conversion_common_ndk.so', 'libaudio_aidl_conversion_common_ndk-dax.so'),
    (
        'vendor/lib64/soundfx/libdlbvolaidl.so',
    ): blob_fixup()
        .replace_needed('android.hardware.audio.effect-V2-ndk.so', 'android.hardware.audio.effect-V3-ndk.so')
        .replace_needed('android.media.audio.common.types-V3-ndk.so', 'android.media.audio.common.types-V4-ndk.so'),
    (
        'vendor/lib64/libaudio_aidl_conversion_common_ndk-dax.so',
    ): blob_fixup()
        .replace_needed('android.media.audio.common.types-V3-ndk.so', 'android.media.audio.common.types-V4-ndk.so')
        .remove_needed('android.hardware.audio.common-V3-ndk.so'),
    (
        'vendor/lib64/libcodec2_soft_ac4dec.so',
        'vendor/lib64/libcodec2_soft_ddpdec.so',
        'vendor/lib64/libcodec2_soft_mha1dec.so',
        'vendor/lib64/libcodec2_soft_mhm1dec.so',
    ): blob_fixup()
        .replace_needed('libcodec2_soft_common.so', 'libcodec2_soft_common-dax.so'),
}  # fmt: skip


class DolbyModule(ExtractUtilsModule):
    # Blobs and generated makefiles live in this repo instead of vendor/
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.vendor_rel_path = self.device_rel_path
        self.vendor_path = self.device_path
        self.vendor_rro_path = path.join(self.vendor_path, 'rro_overlays')

    # The default cleanup wipes vendor_path, which is the whole repo here
    def cleanup(self):
        for proprietary_file in self.proprietary_files:
            vendor_path = self.proprietary_file_vendor_path(proprietary_file)
            os.makedirs(vendor_path, exist_ok=True)
            remove_dir_contents(vendor_path)


module = DolbyModule(
    'dolby',
    'dolby',
    device_rel_path='hardware/dolby',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device(module)
    utils.run()

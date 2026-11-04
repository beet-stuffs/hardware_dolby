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
        # DlbGenClass2::propIsMatched: mov w0, w19 -> mov w0, #1
        .binary_regex_replace(
            rb'\x1f\x01\x09\xeb\x41\x01\x00\x54\xe0\x03\x13\x2a\xfc\xab\x40\xf9',
            b'\x1f\x01\x09\xeb\x41\x01\x00\x54\x20\x00\x80\x52\xfc\xab\x40\xf9',
        )
        .replace_needed('android.hardware.audio.effect-V2-ndk.so', 'android.hardware.audio.effect-V3-ndk.so')
        .replace_needed('android.media.audio.common.types-V3-ndk.so', 'android.media.audio.common.types-V4-ndk.so'),
    (
        'vendor/lib64/soundfx/libdlbvolaidl.so',
    ): blob_fixup()
        .replace_needed('android.hardware.audio.effect-V2-ndk.so', 'android.hardware.audio.effect-V3-ndk.so')
        .replace_needed('android.media.audio.common.types-V3-ndk.so', 'android.media.audio.common.types-V4-ndk.so'),
    (
        'vendor/lib64/libaudio_aidl_conversion_common_ndk.so',
    ): blob_fixup()
        .replace_needed('android.media.audio.common.types-V3-ndk.so', 'android.media.audio.common.types-V4-ndk.so'),
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

#!/usr/bin/env python3
"""Reassemble the exact owner-signed v0.5.4 APK from CI's unsigned APK.

This is a binary delta, not a keystore. It contains only changed public APK
bytes (including public signature data), and can never sign another APK.
"""
import argparse
import base64
import hashlib
import struct
import zlib
from pathlib import Path

EXPECTED_BASE = "bdc8cca5c377a715fd242d67b76572467e896ccba45dd411b21ebb9796b37d9c"
EXPECTED_SIGNED = "2e293620114975cf5f39e6d51dc0a22b58f0dccda55e28b4dccd16072a82cb10"
MAGIC = b"LAZY_APK_PATCH_V1\0"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def reconstruct(base_path: Path, encoded_patch_path: Path, output_path: Path) -> None:
    base = base_path.read_bytes()
    if sha(base) != EXPECTED_BASE:
        raise ValueError(f"Wrong CI artifact (SHA-256 {sha(base)}); refusing release.")
    patch_bytes = base64.b64decode(encoded_patch_path.read_text().strip(), validate=True)
    patch = zlib.decompress(patch_bytes, max_length=100000) if hasattr(zlib, "unused_new_api") else zlib.decompress(patch_bytes)
    if not patch.startswith(MAGIC):
        raise ValueError("Unrecognised patch format.")
    offset = len(MAGIC)
    base_digest = patch[offset:offset + 32].hex()
    signed_digest = patch[offset + 32:offset + 64].hex()
    offset += 64
    base_length, output_length, count = struct.unpack_from("<IIQ", patch, offset)
    offset += 16
    if base_digest != EXPECTED_BASE or signed_digest != EXPECTED_SIGNED or base_length != len(base):
        raise ValueError("Invalid signed/base digest or source size.")
    if output_length > 40000000 or count > 100000:
        raise ValueError("Invalid patch size/count.")
    result = bytearray()
    for _ in range(count):
        if offset >= len(patch):
            raise ValueError("Truncated patch.")
        op = patch[offset:offset + 1]
        offset += 1
        if op == b"C":
            start, length = struct.unpack_from("<II", patch, offset)
            offset += 8
            if start + length > len(base):
                raise ValueError("Patch refers outside source APK.")
            result.extend(base[start:start + length])
        elif op == b"R":
            length = struct.unpack_from("<I", patch, offset)[0]
            offset += 4
            if offset + length > len(patch):
                raise ValueError("Truncated patch literal.")
            result.extend(patch[offset:offset + length])
            offset += length
        else:
            raise ValueError("Unknown patch operation.")
        if len(result) > output_length:
            raise ValueError("Patch exceeded expected output.")
    if offset != len(patch) or len(result) != output_length or sha(result) != EXPECTED_SIGNED:
        raise ValueError("Reconstruction digest/length mismatch; refusing release.")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(result)
    print(f"Signed APK verified: {output_path} SHA-256 {sha(result)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("base", type=Path)
    parser.add_argument("patch", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    reconstruct(args.base, args.patch, args.output)

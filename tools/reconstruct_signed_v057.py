#!/usr/bin/env python3
"""Recover only the exact signed v0.5.7 APK from its exact CI-tested debug APK.

This public binary delta contains APK bytes and signature metadata, not a private key.
It cannot sign any other APK. Mismatched input and output hashes are refused.
"""
import argparse
import base64
import hashlib
import struct
import zlib
from pathlib import Path

EXPECTED_BASE = '384ff32c6c4de951bb09c901e7fb64c20f754eecee7717974d0c180cd373586a'
EXPECTED_SIGNED = '1d40af3998908f1df721a4aa988f4609016a7193c6e791f8da0fbbd8cf4af36d'
MAGIC = b'LAZY_APK_PATCH_V1\0'

def sha(blob):
    return hashlib.sha256(blob).hexdigest()

def reconstruct(base_path, patch_path, output_path):
    base = Path(base_path).read_bytes()
    if sha(base) != EXPECTED_BASE:
        raise ValueError('CI source APK digest does not match tested build')
    encoded = ''.join(Path(patch_path).read_text().split())
    patch = zlib.decompress(base64.b64decode(encoded, validate=True))
    if not patch.startswith(MAGIC):
        raise ValueError('Unrecognised signed APK patch')
    cursor = len(MAGIC)
    source_hash = patch[cursor:cursor+32].hex()
    output_hash = patch[cursor+32:cursor+64].hex()
    cursor += 64
    source_size, output_size, count = struct.unpack_from('<IIQ',patch,cursor)
    cursor += 16
    if source_hash != EXPECTED_BASE or output_hash != EXPECTED_SIGNED or source_size != len(base):
        raise ValueError('Wrong base or signed APK digest')
    if not (0 < output_size < 40_000_000) or count > 100000:
        raise ValueError('Invalid output size or operation count')
    data = bytearray()
    for _ in range(count):
        opcode = patch[cursor:cursor+1]
        cursor += 1
        if opcode == b'C':
            start, amount = struct.unpack_from('<II',patch,cursor)
            cursor += 8
            if start + amount > len(base):
                raise ValueError('Copy out of range')
            data.extend(base[start:start+amount])
        elif opcode == b'R':
            amount = struct.unpack_from('<I',patch,cursor)[0]
            cursor += 4
            if cursor + amount > len(patch):
                raise ValueError('Literal truncated')
            data.extend(patch[cursor:cursor+amount])
            cursor += amount
        else:
            raise ValueError('Invalid patch operation')
        if len(data) > output_size:
            raise ValueError('Output exceeded declared size')
    if cursor != len(patch) or len(data)!=output_size or sha(data)!=EXPECTED_SIGNED:
        raise ValueError('Reconstruction integrity check failed')
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    Path(output_path).write_bytes(data)
    print('Signed APK digest verified: '+EXPECTED_SIGNED)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('base')
    parser.add_argument('patch')
    parser.add_argument('output')
    args=parser.parse_args()
    reconstruct(args.base,args.patch,args.output)

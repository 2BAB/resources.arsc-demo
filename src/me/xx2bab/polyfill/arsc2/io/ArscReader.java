/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xx2bab.polyfill.arsc2.io;

import me.xx2bab.polyfill.arsc2.ArscUtil;
import me.xx2bab.polyfill.arsc2.data.ResChunk;
import me.xx2bab.polyfill.arsc2.data.ResStringBlock;
import me.xx2bab.polyfill.arsc2.data.ResType;
import me.xx2bab.polyfill.arsc2.Log;
import me.xx2bab.polyfill.arsc2.data.ArscConstants;
import me.xx2bab.polyfill.arsc2.data.ResConfig;
import me.xx2bab.polyfill.arsc2.data.ResEntry;
import me.xx2bab.polyfill.arsc2.data.ResMapValue;
import me.xx2bab.polyfill.arsc2.data.ResPackage;
import me.xx2bab.polyfill.arsc2.data.ResTable;
import me.xx2bab.polyfill.arsc2.data.ResTypeSpec;
import me.xx2bab.polyfill.arsc2.data.ResValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jinqiuchen on 18/7/29.
 */

public class ArscReader {

    private static final String TAG = "ArscUtil.ArscReader";

    LittleEndianInputStream dataInput;

    public ArscReader(String arscFile) throws FileNotFoundException {
        dataInput = new LittleEndianInputStream(arscFile);
        Log.d(TAG, String.format("read From %s", arscFile));
    }

    public ResTable readResourceTable() throws IOException {
        Log.d(TAG, "===========================");
        long headStart = 0;
        ResTable resTable = new ResTable();
        resTable.setStart(headStart);
        resTable.setType(dataInput.readShort());
        Log.d(TAG, String.format("table type %d", resTable.getType()));
        resTable.setHeadSize(dataInput.readShort());
        Log.d(TAG, String.format("head size %d", resTable.getHeadSize()));
        resTable.setChunkSize(dataInput.readInt());
        Log.d(TAG, String.format("chunk size %f KB", resTable.getChunkSize() / 1024.0f));
        resTable.setPackageCount(dataInput.readInt());
        Log.d(TAG, String.format("package count %d", resTable.getPackageCount()));
        resTable.setHeadPaddingSize((int) (resTable.getHeadSize() + headStart - dataInput.getFilePointer()));
        dataInput.seek(headStart + resTable.getHeadSize());
        resTable.setGlobalStringPool(readStringBlock());
        Log.d(TAG, String.format("global string pool pos %d", dataInput.getFilePointer()));
        if (resTable.getPackageCount() > 0) {
            ResPackage[] packages = new ResPackage[resTable.getPackageCount()];
            for (int i = 0; i < resTable.getPackageCount(); i++) {
                packages[i] = readPackage();
            }
            resTable.setPackages(packages);
        }
        resTable.setChunkPaddingSize((int) (resTable.getChunkSize() + headStart - dataInput.getFilePointer()));
        dataInput.close();
        return resTable;
    }

    private ResPackage readPackage() throws IOException {
        Log.d(TAG, "===========================");
        long headStart = dataInput.getFilePointer();
        Log.d(TAG, String.format("package start %d", headStart));
        ResPackage resPackage = new ResPackage();
        resPackage.setStart(headStart);
        resPackage.setType(dataInput.readShort());
        Log.d(TAG, String.format("package type %d", resPackage.getType()));
        resPackage.setHeadSize(dataInput.readShort());
        Log.d(TAG, String.format("head size %d", resPackage.getHeadSize()));
        resPackage.setChunkSize(dataInput.readInt());
        Log.d(TAG, String.format("chunk size %d", resPackage.getChunkSize()));
        resPackage.setId(dataInput.readInt());
        Log.d(TAG, String.format("package id %d", resPackage.getId()));
        byte[] buffer = new byte[256];
        dataInput.read(buffer);
        resPackage.setName(buffer);
        Log.d(TAG, String.format("package name %s", ArscUtil.toUTF16String(buffer)));
        resPackage.setResTypePoolOffset(dataInput.readInt());
        Log.d(TAG, String.format("resType pool offset %d", resPackage.getResTypePoolOffset()));
        resPackage.setLastPublicType(dataInput.readInt());
        Log.d(TAG, String.format("lastPublicType index %d", resPackage.getLastPublicType()));
        resPackage.setResNamePoolOffset(dataInput.readInt());
        Log.d(TAG, String.format("resName pool offset %d", resPackage.getResNamePoolOffset()));
        resPackage.setLastPublicName(dataInput.readInt());
        Log.d(TAG, String.format("lastPublicName index %d", resPackage.getLastPublicName()));
        resPackage.setHeadPaddingSize((int) (resPackage.getHeadSize() + headStart - dataInput.getFilePointer()));
        if (resPackage.getResTypePoolOffset() > 0) {
            dataInput.seek(headStart + resPackage.getResTypePoolOffset());
            ResStringBlock resTypePool = readStringBlock();
            resPackage.setResTypePool(resTypePool);
        }
        if (resPackage.getResNamePoolOffset() > 0) {
            dataInput.seek(headStart + resPackage.getResNamePoolOffset());
            ResStringBlock resNamePool = readStringBlock();
            resPackage.setResNamePool(resNamePool);
        }
        List<ResChunk> resTypeList = new ArrayList<ResChunk>();
        while (dataInput.getFilePointer() < (resPackage.getStart() + resPackage.getChunkSize())) {
            int type = dataInput.readShort();
            if (type == ArscConstants.RES_TABLE_TYPE_SPEC_TYPE) {
                dataInput.seek(dataInput.getFilePointer() - 2);
                ResTypeSpec resTypeSpec = readResTypeSpec();
                resTypeList.add(resTypeSpec);
            } else if (type == ArscConstants.RES_TABLE_TYPE_TYPE) {
                dataInput.seek(dataInput.getFilePointer() - 2);
                ResType resType = readResType(resPackage);
                resTypeList.add(resType);
            }
        }
        resPackage.setResTypeArray(resTypeList);
        resPackage.setChunkPaddingSize((int) (resPackage.getChunkSize() + headStart - dataInput.getFilePointer()));
        dataInput.seek(resPackage.getStart() + resPackage.getChunkSize());
        return resPackage;
    }

    private ResTypeSpec readResTypeSpec() throws IOException {
        Log.d(TAG, "===========================");
        long headStart = dataInput.getFilePointer();
        ResTypeSpec resTypeSpec = new ResTypeSpec();
        resTypeSpec.setStart(headStart);
        resTypeSpec.setType(dataInput.readShort());
        Log.d(TAG, String.format("resTypeSpec type %d", resTypeSpec.getType()));
        resTypeSpec.setHeadSize(dataInput.readShort());
        Log.d(TAG, String.format("resTypeSpec header size %d", resTypeSpec.getHeadSize()));
        resTypeSpec.setChunkSize(dataInput.readInt());
        Log.d(TAG, String.format("resTypeSpec chunk size %d", resTypeSpec.getChunkSize()));
        resTypeSpec.setId(dataInput.readByte());
        Log.d(TAG, String.format("resTypeSpec type id %d", resTypeSpec.getId()));
        resTypeSpec.setReserved0(dataInput.readByte());
        resTypeSpec.setReserved1(dataInput.readShort());
        resTypeSpec.setEntryCount(dataInput.readInt());
        Log.d(TAG, String.format("resTypeSpec entry count %d", resTypeSpec.getEntryCount()));
        resTypeSpec.setHeadPaddingSize((int) (resTypeSpec.getHeadSize() + headStart - dataInput.getFilePointer()));
        if (resTypeSpec.getChunkSize() - resTypeSpec.getHeadSize() > 0) {
            byte[] buffer = new byte[resTypeSpec.getChunkSize() - resTypeSpec.getHeadSize()];
            dataInput.read(buffer);
            resTypeSpec.setConfigFlags(buffer);
        }
        resTypeSpec.setChunkPaddingSize((int) (resTypeSpec.getChunkSize() + headStart - dataInput.getFilePointer()));
        dataInput.seek(headStart + resTypeSpec.getChunkSize());
        return resTypeSpec;
    }

    private ResType readResType(ResPackage resPackage) throws IOException {
        Log.d(TAG, "===========================");
        long headStart = dataInput.getFilePointer();
        ResType resType = new ResType();
        resType.setStart(headStart);
        resType.setType(dataInput.readShort());
        Log.d(TAG, String.format("resType type %d", resType.getType()));
        resType.setHeadSize(dataInput.readShort());
        Log.d(TAG, String.format("resType header size %d", resType.getHeadSize()));
        resType.setChunkSize(dataInput.readInt());
        Log.d(TAG, String.format("resType chunk size %d", resType.getChunkSize()));
        resType.setId(dataInput.readByte());
        Log.d(TAG, String.format("resType type id %d", resType.getId()));
        resType.setReserved0(dataInput.readByte());
        resType.setReserved1(dataInput.readShort());
        resType.setEntryCount(dataInput.readInt());
        Log.d(TAG, String.format("resType entry count %d", resType.getEntryCount()));
        resType.setEntryTableOffset(dataInput.readInt());
        Log.d(TAG, String.format("resType entryTable offset %d", resType.getEntryTableOffset()));
        resType.setResConfigFlags(readResConfig());
        resType.setHeadPaddingSize((int) (resType.getHeadSize() + headStart - dataInput.getFilePointer()));
        if (resType.getEntryCount() > 0) {
            List<Integer> resEntryOffsets = new ArrayList<Integer>();
            for (int i = 0; i < resType.getEntryCount(); i++) {
                resEntryOffsets.add(dataInput.readInt());
            }
            resType.setEntryOffsets(resEntryOffsets);
        }
        dataInput.seek(headStart + resType.getEntryTableOffset());
        List<ResEntry> entryTable = new ArrayList<ResEntry>();
        for (int i = 0; i < resType.getEntryCount(); i++) {
            if (resType.getEntryOffsets().get(i) != ArscConstants.NO_ENTRY_INDEX) {
                entryTable.add(readResEntry(resPackage,
                        headStart + resType.getEntryTableOffset() + resType.getEntryOffsets().get(i)));
            } else {
                entryTable.add(null);
            }
        }
        resType.setEntryTable(entryTable);
        resType.setChunkPaddingSize((int) (resType.getChunkSize() + headStart - dataInput.getFilePointer()));
        dataInput.seek(headStart + resType.getChunkSize());
        return resType;
    }

    @SuppressWarnings("PMD")
    private ResEntry readResEntry(ResPackage resPackage, long start) throws IOException {
        //Log.d(TAG, "===========================");
        dataInput.seek(start);
        ResEntry resEntry = new ResEntry();
        resEntry.setSize(dataInput.readShort());
        //Log.d(TAG, "resEntry size %d", resEntry.getSize());
        resEntry.setFlag(dataInput.readShort());
        //Log.d(TAG, "resEntry flag %d", resEntry.getFlag());
        resEntry.setStringPoolIndex(dataInput.readInt());

        //Log.d(TAG, "entryName %s", ArscUtil.resolveStringPoolEntry(resPackage.getResNamePool().getStrings().get(resEntry.getStringPoolIndex()).array(), resPackage.getResNamePool().getCharSet()));
        if ((resEntry.getFlag() & ArscConstants.RES_TABLE_ENTRY_FLAG_COMPLEX) == 0) {
            resEntry.setResValue(readResValue());
        } else {
            resEntry.setParent(dataInput.readInt());
            resEntry.setPairCount(dataInput.readInt());
            if (resEntry.getPairCount() > 0) {
                List<ResMapValue> mapValues = new ArrayList<ResMapValue>();
                for (int i = 0; i < resEntry.getPairCount(); i++) {
                    mapValues.add(readResMapValue());
                }
                resEntry.setResMapValues(mapValues);
            }
        }
        return resEntry;
    }

    private ResValue readResValue() throws IOException {
        //Log.d(TAG,"===========================");
        ResValue resValue = new ResValue();
        resValue.setSize(dataInput.readShort());
        if (resValue.getSize() > 2) {
            byte[] content = new byte[resValue.getSize() - 2];
            dataInput.read(content);
            resValue.setContent(content);
        }
        return resValue;
    }

    private ResMapValue readResMapValue() throws IOException {
        //Log.d(TAG, "===========================");
        ResMapValue resValue = new ResMapValue();
        resValue.setName(dataInput.readInt());
        resValue.setResValue(readResValue());
        return resValue;
    }

    private ResConfig readResConfig() throws IOException {
        //Log.d(TAG, "===========================");
        ResConfig config = new ResConfig();
        config.setSize(dataInput.readInt());
        //Log.d(TAG, "resConfig size %d", config.getSize());
        if (config.getSize() > 4) {
            byte[] buffer = new byte[config.getSize() - 4];
            dataInput.read(buffer);
            config.setContent(buffer);
        }
        return config;
    }

    private ResStringBlock readStringBlock() throws IOException {
        Log.d(TAG, "===========================");
        long headStart = dataInput.getFilePointer();
        ResStringBlock stringPool = new ResStringBlock();
        stringPool.setStart(headStart);
        stringPool.setType(dataInput.readShort());
        Log.d(TAG, String.format("stringPool type %d", stringPool.getType()));
        stringPool.setHeadSize(dataInput.readShort());
        Log.d(TAG, String.format("stringPool head size %d", stringPool.getHeadSize()));
        stringPool.setChunkSize(dataInput.readInt());
        Log.d(TAG, String.format("stringPool chunk size %d", stringPool.getChunkSize()));
        stringPool.setStringCount(dataInput.readInt());
        Log.d(TAG, String.format("stringPool string count %d", stringPool.getStringCount()));
        stringPool.setStyleCount(dataInput.readInt());
        Log.d(TAG, String.format("stringPool style count %d", stringPool.getStyleCount()));
        stringPool.setFlag(dataInput.readInt());
        Log.d(TAG, String.format("stringPool flag %d", stringPool.getFlag()));
        stringPool.setStringStart(dataInput.readInt());
        Log.d(TAG, String.format("stringPool string start %d", stringPool.getStringStart()));
        stringPool.setStyleStart(dataInput.readInt());
        Log.d(TAG, String.format("stringPool style start %d", stringPool.getStyleStart()));
        stringPool.setHeadPaddingSize((int) (stringPool.getHeadSize() + headStart - dataInput.getFilePointer()));
        dataInput.seek(headStart + stringPool.getHeadSize());
        if (stringPool.getStringCount() > 0) {
            List<Integer> stringOffsets = new ArrayList<Integer>();
            for (int i = 0; i < stringPool.getStringCount(); i++) {
                stringOffsets.add(dataInput.readInt());
            }
            stringPool.setStringOffsets(stringOffsets);
        }
        if (stringPool.getStyleCount() > 0) {
            List<Integer> styleOffsets = new ArrayList<Integer>();
            for (int i = 0; i < stringPool.getStyleCount(); i++) {
                styleOffsets.add(dataInput.readInt());
            }
            stringPool.setStyleOffsets(styleOffsets);
        }
        dataInput.seek(headStart + stringPool.getStringStart());
        if (stringPool.getStringCount() > 0) {
            List<ByteBuffer> strings = new ArrayList<ByteBuffer>();
            for (int i = 0; i < stringPool.getStringCount(); i++) {
                byte[] buffer = null;
                if (i < stringPool.getStringCount() - 1) {
                    buffer = new byte[stringPool.getStringOffsets().get(i + 1) - stringPool.getStringOffsets().get(i)];
                } else {
                    if (stringPool.getStyleCount() > 0) {
                        buffer = new byte[stringPool.getStyleStart() - (stringPool.getStringOffsets().get(i) + stringPool.getStringStart())];
                    } else {
                        buffer = new byte[stringPool.getChunkSize() - stringPool.getStringStart() - stringPool.getStringOffsets().get(i)];
                    }
                }
                dataInput.read(buffer);
                strings.add(ByteBuffer.allocate(buffer.length));
                strings.get(i).order(ByteOrder.LITTLE_ENDIAN);
                strings.get(i).clear();
                strings.get(i).put(buffer);
            }
            stringPool.setStrings(strings);
        }
        if (stringPool.getStyleCount() > 0) {
            byte[] styleBytes = new byte[stringPool.getChunkSize() - stringPool.getStyleStart()];
            dataInput.read(styleBytes);
            stringPool.setStyles(styleBytes);
        }
        stringPool.setChunkPaddingSize((int) (stringPool.getChunkSize() + headStart - dataInput.getFilePointer()));
        dataInput.seek(headStart + stringPool.getChunkSize());
        return stringPool;
    }
}

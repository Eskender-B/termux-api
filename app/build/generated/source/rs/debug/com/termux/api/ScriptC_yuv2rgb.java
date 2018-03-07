/*
 * Copyright (C) 2011-2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file is auto-generated. DO NOT MODIFY!
 * The source Renderscript file: /home/eskender/Desktop/selfDrivingCar/termux-api/app/src/main/rs/yuv2rgb.rs
 */

package com.termux.api;

import android.os.Build;
import android.os.Process;
import java.lang.reflect.Field;
import android.renderscript.*;
import com.termux.api.yuv2rgbBitCode;

/**
 * @hide
 */
public class ScriptC_yuv2rgb extends ScriptC {
    private static final String __rs_resource_name = "yuv2rgb";
    // Constructor
    public  ScriptC_yuv2rgb(RenderScript rs) {
        super(rs,
              __rs_resource_name,
              yuv2rgbBitCode.getBitCode32(),
              yuv2rgbBitCode.getBitCode64());
        __ALLOCATION = Element.ALLOCATION(rs);
        __U8_4 = Element.U8_4(rs);
    }

    private Element __ALLOCATION;
    private Element __U8_4;
    private FieldPacker __rs_fp_ALLOCATION;
    private final static int mExportVarIdx_gCurrentFrame = 0;
    private Allocation mExportVar_gCurrentFrame;
    public synchronized void set_gCurrentFrame(Allocation v) {
        setVar(mExportVarIdx_gCurrentFrame, v);
        mExportVar_gCurrentFrame = v;
    }

    public Allocation get_gCurrentFrame() {
        return mExportVar_gCurrentFrame;
    }

    public Script.FieldID getFieldID_gCurrentFrame() {
        return createFieldID(mExportVarIdx_gCurrentFrame, null);
    }

    private final static int mExportVarIdx_gIntFrame = 1;
    private Allocation mExportVar_gIntFrame;
    public synchronized void set_gIntFrame(Allocation v) {
        setVar(mExportVarIdx_gIntFrame, v);
        mExportVar_gIntFrame = v;
    }

    public Allocation get_gIntFrame() {
        return mExportVar_gIntFrame;
    }

    public Script.FieldID getFieldID_gIntFrame() {
        return createFieldID(mExportVarIdx_gIntFrame, null);
    }

    //private final static int mExportForEachIdx_root = 0;
    private final static int mExportForEachIdx_yuv2rgbFrames = 1;
    public Script.KernelID getKernelID_yuv2rgbFrames() {
        return createKernelID(mExportForEachIdx_yuv2rgbFrames, 59, null, null);
    }

    public void forEach_yuv2rgbFrames(Allocation ain, Allocation aout) {
        forEach_yuv2rgbFrames(ain, aout, null);
    }

    public void forEach_yuv2rgbFrames(Allocation ain, Allocation aout, Script.LaunchOptions sc) {
        // check ain
        if (!ain.getType().getElement().isCompatible(__U8_4)) {
            throw new RSRuntimeException("Type mismatch with U8_4!");
        }
        // check aout
        if (!aout.getType().getElement().isCompatible(__U8_4)) {
            throw new RSRuntimeException("Type mismatch with U8_4!");
        }
        Type t0, t1;        // Verify dimensions
        t0 = ain.getType();
        t1 = aout.getType();
        if ((t0.getCount() != t1.getCount()) ||
            (t0.getX() != t1.getX()) ||
            (t0.getY() != t1.getY()) ||
            (t0.getZ() != t1.getZ()) ||
            (t0.hasFaces()   != t1.hasFaces()) ||
            (t0.hasMipmaps() != t1.hasMipmaps())) {
            throw new RSRuntimeException("Dimension mismatch between parameters ain and aout!");
        }

        forEach(mExportForEachIdx_yuv2rgbFrames, ain, aout, null, sc);
    }

}


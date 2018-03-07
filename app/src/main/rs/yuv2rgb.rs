#pragma version(1)
#pragma rs java_package_name(com.termux.api)
#pragma rs_fp_relaxed

rs_allocation gCurrentFrame;
rs_allocation gIntFrame;

uchar4 __attribute__((kernel)) yuv2rgbFrames(uchar4 prevPixel,uint32_t x,uint32_t y)
{

    // Read in pixel values from latest frame - YUV color space
    // The functions rsGetElementAtYuv_uchar_? require API 18
    uchar4 curPixel;
    curPixel.r = rsGetElementAtYuv_uchar_Y(gCurrentFrame, x, y);
    curPixel.g = rsGetElementAtYuv_uchar_U(gCurrentFrame, x, y);
    curPixel.b = rsGetElementAtYuv_uchar_V(gCurrentFrame, x, y);

    // uchar4 rsYuvToRGBA_uchar4(uchar y, uchar u, uchar v);
    // This function uses the NTSC formulae to convert YUV to RBG
    uchar4 out = rsYuvToRGBA_uchar4(curPixel.r, curPixel.g, curPixel.b);

    uint32_t px = 0xff000000 | out.r << 16 | out.g << 8 | out.b;
    rsSetElementAt_int(gIntFrame, px, x, y);
    return out;
}
# Termux Api

* This repo adds video streaming option to the real [termux-api](https://github.com/termux/termux-api) 
* Frame of 320X240 is currently streamed on Unix Anonymous socket.
* BuiltIn RenderScript is used for conversion of YUV image to RGB format
* Files which are added are:
	* [VideoService.java]( app/src/main/java/com/termux/api/VideoService.java)
	* [ConnectionEstablisher.java](app/src/main/java/com/termux/api/util/ConnectionEstablisher.java)
	* [ImageUtils.java](app/src/main/java/com/termux/api/util/ImageUtils.java)

* See  [termux-api](https://github.com/termux/termux-api) readme for more info.

## Issues
* Renderscript YUV to RGB is not quite right. It always outputs black and white image. (Need to fix this)

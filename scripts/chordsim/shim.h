/* The smallest environment the three lifted functions touch. Nothing here is shipped code. */
#pragma once
#include <stdatomic.h>
#include <stddef.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

typedef signed char jbyte;
typedef int jint;

#define EVENT_WINDOW_SIZE 8000
#define EVENT_TYPE_CHAR 1000
#define EVENT_TYPE_CHAR_MODS 1001
#define EVENT_TYPE_CURSOR_ENTER 1002
#define EVENT_TYPE_CURSOR_POS 1003
#define EVENT_TYPE_KEY 1004
#define EVENT_TYPE_MOUSE_BUTTON 1005
#define EVENT_TYPE_SCROLL 1006
#define EVENT_TYPE_FRAMEBUFFER_SIZE 1007
#define EVENT_TYPE_WINDOW_SIZE 1008

typedef struct { int type, i1, i2, i3, i4; } GLFWInputEvent;

typedef void GLFW_invoke_Char_func(void*, unsigned int);
typedef void GLFW_invoke_CharMods_func(void*, unsigned int, int);
typedef void GLFW_invoke_CursorEnter_func(void*, int);
typedef void GLFW_invoke_CursorPos_func(void*, double, double);
typedef void GLFW_invoke_Key_func(void*, int, int, int, int);
typedef void GLFW_invoke_MouseButton_func(void*, int, int, int);
typedef void GLFW_invoke_Scroll_func(void*, double, double);

struct pojav_environ_s {
    GLFWInputEvent events[EVENT_WINDOW_SIZE];
    size_t outEventIndex, outTargetIndex, inEventIndex;
    _Atomic size_t eventCounter;
    double cursorX, cursorY;
    int shouldUpdateMouse, shouldUpdateMonitorSize;
    int isUseStackQueueCall, isInputReady;
    long showingWindow;
    jbyte* keyDownBuffer;
    GLFW_invoke_Char_func* GLFW_invoke_Char;
    GLFW_invoke_CharMods_func* GLFW_invoke_CharMods;
    GLFW_invoke_CursorEnter_func* GLFW_invoke_CursorEnter;
    GLFW_invoke_CursorPos_func* GLFW_invoke_CursorPos;
    GLFW_invoke_Key_func* GLFW_invoke_Key;
    GLFW_invoke_MouseButton_func* GLFW_invoke_MouseButton;
    GLFW_invoke_Scroll_func* GLFW_invoke_Scroll;
};

extern struct pojav_environ_s* pojav_environ;
void updateWindowSize(void* window);
void sendData(int type, int i1, int i2, int i3, int i4);
void pojavPumpEvents(void* window);
void critical_send_key(jint key, jint scancode, jint action, jint mods);

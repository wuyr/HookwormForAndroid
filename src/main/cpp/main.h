#pragma once
#define EXPORT __attribute__((visibility("default"))) __attribute__((used))
#define INJECT_CLASS_PATH "com/wuyr/hookworm/core/Main"
#define DEX_PATH ""
char *process_name;
const char *target_process_name[] = {};
const int target_process_size = 1;
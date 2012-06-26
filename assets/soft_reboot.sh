#!/system/bin/sh
export PATH=/system/bin:$PATH

setprop xposed.libs.testmode 1
setprop ctl.restart surfaceflinger
setprop ctl.restart zygote
